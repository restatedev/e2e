// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.client.experimental;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.restate.client.Client;
import dev.restate.client.IngressException;
import dev.restate.client.RequestOptions;
import dev.restate.client.Response;
import dev.restate.client.ResponseHead;
import dev.restate.client.SendResponse;
import dev.restate.common.Output;
import dev.restate.common.Request;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeRef;
import dev.restate.serde.TypeTag;
import dev.restate.serde.provider.DefaultSerdeFactoryProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link Client} that targets the new {@code /restate/*} ingress API introduced
 * in <a href="https://github.com/restatedev/restate/pull/4678">restatedev/restate#4678</a>:
 *
 * <ul>
 *   <li>{@code POST /restate/call/{service}[/{key}]/{handler}}
 *   <li>{@code POST /restate/send/{service}[/{key}]/{handler}}
 *   <li>{@code GET /restate/attach/{invocationId}} and {@code GET /restate/output/{invocationId}}
 *   <li>{@code POST /restate/attach} and {@code POST /restate/output} with an invocation target
 *       body
 * </ul>
 *
 * <p>This class is intentionally self-contained (it does not extend the SDK's {@code BaseClient})
 * so that it can be copy-pasted into the SDK {@code client} module once the new ingress API
 * stabilizes. It mirrors the request serialization, header handling and response mapping of {@code
 * BaseClient} / {@code JdkClient}, only the URL routing differs. JSON bodies are produced with the
 * Jackson Core streaming API to match the minimal dependency set of the SDK {@code client} module.
 */
public final class NewIngressClient implements Client {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private final HttpClient httpClient;
  private final URI baseUri;
  private final SerdeFactory serdeFactory;
  private final RequestOptions baseOptions;

  private NewIngressClient(
      HttpClient httpClient, URI baseUri, SerdeFactory serdeFactory, RequestOptions baseOptions) {
    this.baseUri = Objects.requireNonNull(baseUri, "Base uri cannot be null");
    if (!this.baseUri.isAbsolute()) {
      throw new IllegalArgumentException(
          "The base uri " + baseUri + " is not absolute. This is not supported.");
    }
    this.httpClient = httpClient;
    this.serdeFactory = serdeFactory == null ? loadDefaultSerdeFactory() : serdeFactory;
    this.baseOptions = baseOptions == null ? RequestOptions.DEFAULT : baseOptions;
  }

  /** Create a new client connecting to the given base uri. */
  public static NewIngressClient connect(String baseUri) {
    return connect(baseUri, null, null);
  }

  /** Create a new client connecting to the given base uri, with the given serde factory/options. */
  public static NewIngressClient connect(
      String baseUri, SerdeFactory serdeFactory, RequestOptions options) {
    return new NewIngressClient(
        HttpClient.newHttpClient(), URI.create(baseUri), serdeFactory, options);
  }

  // --- Call/Send

  @Override
  public <Req, Res> CompletableFuture<Response<Res>> callAsync(Request<Req, Res> request) {
    Serde<Req> reqSerde = this.serdeFactory.create(request.getRequestTypeTag());
    Serde<Res> resSerde = this.serdeFactory.create(request.getResponseTypeTag());

    URI requestUri = baseUri.resolve("/restate/call" + targetToURI(request.getTarget()));
    Stream<Map.Entry<String, String>> headersStream =
        requestHeaders(request, reqSerde.contentType());
    Slice requestBody = reqSerde.serialize(request.getRequest());

    return doPostRequest(
        requestUri, headersStream, requestBody, callResponseMapper("POST", requestUri, resSerde));
  }

  @Override
  public <Req, Res> CompletableFuture<SendResponse<Res>> sendAsync(
      Request<Req, Res> request, Duration delay) {
    Serde<Req> reqSerde = this.serdeFactory.create(request.getRequestTypeTag());

    StringBuilder path =
        new StringBuilder("/restate/send").append(targetToURI(request.getTarget()));
    if (delay != null && !delay.isZero() && !delay.isNegative()) {
      path.append("?delay=").append(delay);
    }
    URI requestUri = baseUri.resolve(path.toString());

    Stream<Map.Entry<String, String>> headersStream =
        requestHeaders(request, reqSerde.contentType());
    Slice requestBody = reqSerde.serialize(request.getRequest());

    return doPostRequest(
        requestUri,
        headersStream,
        requestBody,
        (statusCode, responseHeaders, responseBody) -> {
          if (statusCode >= 300) {
            handleNonSuccessResponse(
                "POST", requestUri.toString(), statusCode, responseHeaders, responseBody);
          }

          if (responseBody == null) {
            throw new IngressException(
                "Expecting a response body, but got none",
                "POST",
                requestUri.toString(),
                statusCode,
                null,
                null);
          }

          Map<String, String> fields;
          try {
            fields =
                findStringFieldsInJsonObject(
                    new ByteArrayInputStream(responseBody.toByteArray()), "invocationId", "status");
          } catch (Exception e) {
            throw new IngressException(
                "Cannot deserialize the response",
                "POST",
                requestUri.toString(),
                statusCode,
                responseBody.toByteArray(),
                e);
          }

          String statusField = fields.get("status");
          SendResponse.SendStatus status;
          if ("Accepted".equalsIgnoreCase(statusField)) {
            status = SendResponse.SendStatus.ACCEPTED;
          } else if ("PreviouslyAccepted".equalsIgnoreCase(statusField)) {
            status = SendResponse.SendStatus.PREVIOUSLY_ACCEPTED;
          } else {
            throw new IngressException(
                "Cannot deserialize the response status, got " + statusField,
                "POST",
                requestUri.toString(),
                statusCode,
                responseBody.toByteArray(),
                null);
          }

          return new SendResponse<>(
              statusCode,
              responseHeaders,
              status,
              invocationHandle(fields.get("invocationId"), request.getResponseTypeTag()));
        });
  }

  // --- Awakeables (unchanged by the new ingress API)

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> CompletableFuture<Response<Void>> resolveAsync(
          TypeTag<T> serde, T payload, RequestOptions options) {
        Serde<T> reqSerde = serdeFactory.create(serde);
        Slice requestBody = reqSerde.serialize(payload);

        URI requestUri = baseUri.resolve("/restate/awakeables/" + id + "/resolve");
        Stream<Map.Entry<String, String>> headersStream = mergeHeaders(options);
        if (reqSerde.contentType() != null) {
          headersStream =
              Stream.concat(
                  headersStream, Stream.of(Map.entry("content-type", reqSerde.contentType())));
        }

        return doPostRequest(
            requestUri,
            headersStream,
            requestBody,
            handleVoidResponse("POST", requestUri.toString()));
      }

      @Override
      public CompletableFuture<Response<Void>> rejectAsync(String reason, RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/awakeables/" + id + "/reject");
        Stream<Map.Entry<String, String>> headersStream =
            Stream.concat(
                mergeHeaders(options), Stream.of(Map.entry("content-type", "text/plain")));

        return doPostRequest(
            requestUri,
            headersStream,
            Slice.wrap(reason),
            handleVoidResponse("POST", requestUri.toString()));
      }
    };
  }

  // --- Attach/Output by invocation id

  @Override
  public <Res> InvocationHandle<Res> invocationHandle(
      String invocationId, TypeTag<Res> resTypeTag) {
    Serde<Res> resSerde = serdeFactory.create(resTypeTag);

    return new InvocationHandle<>() {
      @Override
      public String invocationId() {
        return invocationId;
      }

      @Override
      public CompletableFuture<Response<Res>> attachAsync(RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/attach/" + invocationId);
        return doGetRequest(
            requestUri, mergeHeaders(options), callResponseMapper("GET", requestUri, resSerde));
      }

      @Override
      public CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/output/" + invocationId);
        return doGetRequest(
            requestUri,
            mergeHeaders(options),
            getOutputResponseMapper("GET", requestUri, resSerde));
      }

      @Override
      public String toString() {
        return "InvocationHandle{" + invocationId + "}";
      }
    };
  }

  // --- Attach/Output by idempotency target

  @Override
  public <Res> IdempotentInvocationHandle<Res> idempotentInvocationHandle(
      Target target, String idempotencyKey, TypeTag<Res> resTypeTag) {
    Serde<Res> resSerde = serdeFactory.create(resTypeTag);
    Slice body = idempotencyTargetBody(target, idempotencyKey);

    return new IdempotentInvocationHandle<>() {
      @Override
      public CompletableFuture<Response<Res>> attachAsync(RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/attach");
        return doPostRequest(
            requestUri,
            jsonHeaders(options),
            body,
            callResponseMapper("POST", requestUri, resSerde));
      }

      @Override
      public CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/output");
        return doPostRequest(
            requestUri,
            jsonHeaders(options),
            body,
            getOutputResponseMapper("POST", requestUri, resSerde));
      }
    };
  }

  // --- Attach/Output by workflow target

  @Override
  public <Res> WorkflowHandle<Res> workflowHandle(
      String workflowName, String workflowId, TypeTag<Res> resTypeTag) {
    Serde<Res> resSerde = serdeFactory.create(resTypeTag);
    Slice body = workflowTargetBody(workflowName, workflowId);

    return new WorkflowHandle<>() {
      @Override
      public CompletableFuture<Response<Res>> attachAsync(RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/attach");
        return doPostRequest(
            requestUri,
            jsonHeaders(options),
            body,
            callResponseMapper("POST", requestUri, resSerde));
      }

      @Override
      public CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options) {
        URI requestUri = baseUri.resolve("/restate/output");
        return doPostRequest(
            requestUri,
            jsonHeaders(options),
            body,
            getOutputResponseMapper("POST", requestUri, resSerde));
      }
    };
  }

  // --- HTTP transport (mirrors JdkClient)

  @FunctionalInterface
  private interface ResponseMapper<R> {
    R mapResponse(int statusCode, ResponseHead.Headers responseHeaders, Slice responseBody);
  }

  private <R> CompletableFuture<R> doPostRequest(
      URI target,
      Stream<Map.Entry<String, String>> headers,
      Slice payload,
      ResponseMapper<R> responseMapper) {
    var reqBuilder = HttpRequest.newBuilder().uri(target);
    headers.forEach(h -> reqBuilder.header(h.getKey(), h.getValue()));
    reqBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()));

    return this.httpClient
        .sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (res, t) -> {
              if (t != null) {
                throw new IngressException(
                    "Error when executing the request: " + t.getMessage(),
                    "POST",
                    target.toString(),
                    -1,
                    null,
                    t);
              }
              return responseMapper.mapResponse(
                  res.statusCode(), toHeaders(res.headers()), Slice.wrap(res.body()));
            });
  }

  private <R> CompletableFuture<R> doGetRequest(
      URI target, Stream<Map.Entry<String, String>> headers, ResponseMapper<R> responseMapper) {
    var reqBuilder = HttpRequest.newBuilder().uri(target);
    headers.forEach(h -> reqBuilder.header(h.getKey(), h.getValue()));
    reqBuilder.GET();

    return this.httpClient
        .sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .handle(
            (res, t) -> {
              if (t != null) {
                throw new IngressException(
                    "Error when executing the request: " + t.getMessage(),
                    "GET",
                    target.toString(),
                    -1,
                    null,
                    t);
              }
              return responseMapper.mapResponse(
                  res.statusCode(), toHeaders(res.headers()), Slice.wrap(res.body()));
            });
  }

  private ResponseHead.Headers toHeaders(HttpHeaders httpHeaders) {
    return new ResponseHead.Headers() {
      @Override
      public String get(String key) {
        return httpHeaders.firstValue(key).orElse(null);
      }

      @Override
      public Set<String> keys() {
        return httpHeaders.map().keySet();
      }

      @Override
      public Map<String, String> toLowercaseMap() {
        return httpHeaders.map().entrySet().stream()
            .map(e -> Map.entry(e.getKey().toLowerCase(), e.getValue().get(0)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      }
    };
  }

  // --- Header helpers

  private Stream<Map.Entry<String, String>> mergeHeaders(RequestOptions options) {
    return Stream.concat(
        baseOptions.headers().entrySet().stream(), options.headers().entrySet().stream());
  }

  private Stream<Map.Entry<String, String>> jsonHeaders(RequestOptions options) {
    return Stream.concat(
        mergeHeaders(options), Stream.of(Map.entry("content-type", "application/json")));
  }

  private Stream<Map.Entry<String, String>> requestHeaders(
      Request<?, ?> request, String contentType) {
    Stream<Map.Entry<String, String>> headersStream =
        Stream.concat(
            baseOptions.headers().entrySet().stream(),
            request.getHeaders() == null
                ? Stream.empty()
                : request.getHeaders().entrySet().stream());
    if (contentType != null) {
      headersStream =
          Stream.concat(headersStream, Stream.of(Map.entry("content-type", contentType)));
    }
    if (request.getIdempotencyKey() != null) {
      headersStream =
          Stream.concat(
              headersStream, Stream.of(Map.entry("idempotency-key", request.getIdempotencyKey())));
    }
    return headersStream;
  }

  // --- URL/body helpers

  /** Contains prefix / but not postfix /. */
  private static String targetToURI(Target target) {
    StringBuilder builder = new StringBuilder();
    builder.append("/").append(target.getService());
    if (target.getKey() != null) {
      builder.append("/").append(urlEncode(target.getKey()));
    }
    builder.append("/").append(target.getHandler());
    return builder.toString();
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static Slice idempotencyTargetBody(Target target, String idempotencyKey) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (JsonGenerator gen = JSON_FACTORY.createGenerator(baos)) {
      gen.writeStartObject();
      gen.writeStringField("type", "idempotency");
      gen.writeStringField("service", target.getService());
      if (target.getKey() != null) {
        gen.writeStringField("serviceKey", target.getKey());
      }
      gen.writeStringField("handler", target.getHandler());
      gen.writeStringField("idempotencyKey", idempotencyKey);
      gen.writeEndObject();
    } catch (IOException e) {
      throw new RuntimeException("Cannot serialize idempotency invocation target", e);
    }
    return Slice.wrap(baos.toByteArray());
  }

  private static Slice workflowTargetBody(String workflowName, String workflowId) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (JsonGenerator gen = JSON_FACTORY.createGenerator(baos)) {
      gen.writeStartObject();
      gen.writeStringField("type", "workflow");
      gen.writeStringField("name", workflowName);
      gen.writeStringField("key", workflowId);
      gen.writeEndObject();
    } catch (IOException e) {
      throw new RuntimeException("Cannot serialize workflow invocation target", e);
    }
    return Slice.wrap(baos.toByteArray());
  }

  // --- Response mappers (mirror BaseClient)

  private <Res> ResponseMapper<Response<Res>> callResponseMapper(
      String requestMethod, URI requestUri, Serde<Res> resSerde) {
    return (statusCode, responseHeaders, responseBody) -> {
      if (statusCode >= 300) {
        handleNonSuccessResponse(
            requestMethod, requestUri.toString(), statusCode, responseHeaders, responseBody);
      }
      if (responseBody == null) {
        throw new IngressException(
            "Expecting a response body, but got none",
            requestMethod,
            requestUri.toString(),
            statusCode,
            null,
            null);
      }
      try {
        return new Response<>(statusCode, responseHeaders, resSerde.deserialize(responseBody));
      } catch (Exception e) {
        throw new IngressException(
            "Cannot deserialize the response",
            requestMethod,
            requestUri.toString(),
            statusCode,
            responseBody.toByteArray(),
            e);
      }
    };
  }

  private <Res> ResponseMapper<Response<Output<Res>>> getOutputResponseMapper(
      String requestMethod, URI requestUri, Serde<Res> resSerde) {
    return (statusCode, responseHeaders, responseBody) -> {
      if (statusCode == 470) {
        return new Response<>(statusCode, responseHeaders, Output.notReady());
      }
      if (statusCode >= 300) {
        handleNonSuccessResponse(
            requestMethod, requestUri.toString(), statusCode, responseHeaders, responseBody);
      }
      if (responseBody == null) {
        throw new IngressException(
            "Expecting a response body, but got none",
            requestMethod,
            requestUri.toString(),
            statusCode,
            null,
            null);
      }
      try {
        return new Response<>(
            statusCode, responseHeaders, Output.ready(resSerde.deserialize(responseBody)));
      } catch (Exception e) {
        throw new IngressException(
            "Cannot deserialize the response",
            requestMethod,
            requestUri.toString(),
            statusCode,
            responseBody.toByteArray(),
            e);
      }
    };
  }

  private ResponseMapper<Response<Void>> handleVoidResponse(
      String requestMethod, String requestURI) {
    return (statusCode, responseHeaders, responseBody) -> {
      if (statusCode >= 300) {
        handleNonSuccessResponse(
            requestMethod, requestURI, statusCode, responseHeaders, responseBody);
      }
      return new Response<>(statusCode, responseHeaders, null);
    };
  }

  private void handleNonSuccessResponse(
      String requestMethod,
      String requestURI,
      int statusCode,
      ResponseHead.Headers headers,
      Slice responseBody) {
    String ct = headers.get("content-type");
    if (ct != null && ct.contains("application/json") && responseBody != null) {
      String errorMessage;
      try {
        errorMessage =
            findStringFieldInJsonObject(
                new ByteArrayInputStream(responseBody.toByteArray()), "message");
      } catch (Exception e) {
        throw new IngressException(
            "Can't decode error response from ingress",
            requestMethod,
            requestURI,
            statusCode,
            responseBody.toByteArray(),
            e);
      }
      throw new IngressException(
          errorMessage, requestMethod, requestURI, statusCode, responseBody.toByteArray(), null);
    }

    throw new IngressException(
        "Received non success status code",
        requestMethod,
        requestURI,
        statusCode,
        (responseBody != null) ? responseBody.toByteArray() : null,
        null);
  }

  private static String findStringFieldInJsonObject(InputStream body, String fieldName)
      throws IOException {
    try (JsonParser parser = JSON_FACTORY.createParser(body)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException(
            "Expecting token " + JsonToken.START_OBJECT + ", got " + parser.getCurrentToken());
      }
      for (String actualFieldName = parser.nextFieldName();
          actualFieldName != null;
          actualFieldName = parser.nextFieldName()) {
        if (actualFieldName.equalsIgnoreCase(fieldName)) {
          return parser.nextTextValue();
        } else {
          parser.nextValue();
        }
      }
      throw new IllegalStateException(
          "Expecting field name \"" + fieldName + "\", got " + parser.getCurrentToken());
    }
  }

  private static Map<String, String> findStringFieldsInJsonObject(
      InputStream body, String... fields) throws IOException {
    Map<String, String> resultMap = new HashMap<>();
    Set<String> fieldSet = new HashSet<>(Set.of(fields));

    try (JsonParser parser = JSON_FACTORY.createParser(body)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException(
            "Expecting token " + JsonToken.START_OBJECT + ", got " + parser.getCurrentToken());
      }
      for (String actualFieldName = parser.nextFieldName();
          actualFieldName != null;
          actualFieldName = parser.nextFieldName()) {
        if (fieldSet.remove(actualFieldName)) {
          resultMap.put(actualFieldName, parser.nextTextValue());
        } else {
          parser.nextValue();
        }
      }
    }

    if (!fieldSet.isEmpty()) {
      throw new IllegalStateException(
          "Expecting fields \"" + Arrays.toString(fields) + "\", cannot find fields " + fieldSet);
    }

    return resultMap;
  }

  // --- Default serde factory loading (mirrors BaseClient)

  private static SerdeFactory loadDefaultSerdeFactory() {
    var loadedFactories = ServiceLoader.load(DefaultSerdeFactoryProvider.class).stream().toList();
    if (loadedFactories.size() == 1) {
      return loadedFactories.get(0).get().create();
    }
    return new SerdeFactory() {
      @Override
      public <T> Serde<T> create(TypeRef<T> typeRef) {
        throw new UnsupportedOperationException(
            "No SerdeFactory class was configured. Please configure one, this is required when using"
                + " TypeTag and Class in client methods.");
      }

      @Override
      public <T> Serde<T> create(Class<T> clazz) {
        throw new UnsupportedOperationException(
            "No SerdeFactory class was configured. Please configure one, this is required when using"
                + " TypeTag and Class in client methods.");
      }
    };
  }
}

// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
import dev.restate.candidate.api.DeploymentApi;
import dev.restate.candidate.api.InvocationApi;
import dev.restate.candidate.client.ApiClient;
import dev.restate.candidate.client.ApiException;
import dev.restate.candidate.model.HttpDeploymentType;
import dev.restate.candidate.model.HttpDetailedDeploymentResponse;
import dev.restate.candidate.model.UpdateDeploymentRequest;
import dev.restate.candidate.model.UpdateHttpDeploymentRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;

/** Compiled at test time against the client generated from the candidate's /openapi. */
public class AdminSmoke {
  public static void run(URI admin, String completedInvocationId) throws Exception {
    var requestUri = new AtomicReference<URI>();
    var client = new ApiClient().setHost(admin.getHost()).setPort(admin.getPort());
    client.setRequestInterceptor(builder -> requestUri.set(builder.build().uri()));
    var deployments = new DeploymentApi(client);
    var list = deployments.listDeployments().getDeployments();
    Assertions.assertThat(list).hasSize(1);
    var http = list.getFirst().getHttpDeploymentResponse();
    Assertions.assertThat(http.getType()).isEqualTo(HttpDeploymentType.HTTP);
    Assertions.assertThat(http.getUri()).isNotNull();
    Assertions.assertThat(http.getServices()).hasSize(3);

    var id = http.getId();
    var details = deployments.getDeployment(id).getHttpDetailedDeploymentResponse();
    checkDetails(details, id);

    // Exercise a real update, including re-discovery of the SDK endpoint.
    var headers = Map.of("x-openapi-smoke", "updated");
    var update =
        new UpdateDeploymentRequest(
            new UpdateHttpDeploymentRequest().uri(http.getUri()).additionalHeaders(headers));
    var updated = deployments.updateDeployment(id, update).getHttpDetailedDeploymentResponse();
    checkDetails(updated, id);
    Assertions.assertThat(updated.getAdditionalHeaders()).containsAllEntriesOf(headers);
    Assertions.assertThat(
            deployments
                .getDeployment(id)
                .getHttpDetailedDeploymentResponse()
                .getAdditionalHeaders())
        .containsAllEntriesOf(headers);

    var invocations = new InvocationApi(client);
    for (String selector : new String[] {"keep", "latest", id}) {
      // The completed invocation must reach the handler and produce its documented conflict,
      // rather than failing query deserialization. No suspended invocation fixture is needed.
      try {
        invocations.resumeInvocation(completedInvocationId, selector);
        throw new AssertionError("Resuming a completed invocation should fail");
      } catch (ApiException e) {
        Assertions.assertThat(e.getCode()).isEqualTo(409);
      }
      checkSelector(requestUri.get(), selector);

      var restarted = invocations.restartAsNewInvocation(completedInvocationId, 0, selector);
      Assertions.assertThat(restarted.getNewInvocationId())
          .startsWith("inv_")
          .isNotEqualTo(completedInvocationId);
      checkSelector(requestUri.get(), selector);
    }
  }

  private static void checkDetails(HttpDetailedDeploymentResponse response, String id) {
    Assertions.assertThat(response.getType()).isEqualTo(HttpDeploymentType.HTTP);
    Assertions.assertThat(response.getId()).isEqualTo(id);
    Assertions.assertThat(response.getServices()).hasSize(3);
    Assertions.assertThat(response.getUri()).isNotNull();
  }

  private static void checkSelector(URI uri, String selector) {
    Assertions.assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8).split("&"))
        .contains("deployment=" + selector);
  }
}

package dev.restate.e2e.services.errors;

import com.google.protobuf.Empty;
import com.google.rpc.Code;
import dev.restate.e2e.services.errors.ErrorsProto.ErrorMessage;
import dev.restate.e2e.services.errors.ErrorsProto.FailRequest;
import dev.restate.e2e.services.utils.NumberSortHttpServerUtils;
import dev.restate.sdk.blocking.Awakeable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.TypeTag;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FailingService extends FailingServiceGrpc.FailingServiceImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(FailingService.class);

  @Override
  public void fail(ErrorMessage request, StreamObserver<Empty> responseObserver) {
    LOG.info("Invoked fail");

    throw Status.UNKNOWN.withDescription(request.getErrorMessage()).asRuntimeException();
  }

  @Override
  public void failAndHandle(ErrorMessage request, StreamObserver<ErrorMessage> responseObserver) {
    var ctx = restateContext();
    LOG.info("Invoked failAndHandle");

    try {
      ctx.call(
              FailingServiceGrpc.getFailMethod(),
              request.toBuilder()
                  .setKey(ctx.sideEffect(TypeTag.STRING_UTF8, () -> UUID.randomUUID().toString()))
                  .build())
          .await();
    } catch (StatusRuntimeException e) {
      responseObserver.onNext(
          ErrorMessage.newBuilder().setErrorMessage(e.getStatus().getDescription()).build());
      responseObserver.onCompleted();
      return;
    }

    throw new IllegalStateException("This should be unreachable");
  }

  @Override
  public void invokeExternalAndHandleFailure(
      FailRequest request, StreamObserver<ErrorMessage> responseObserver) {
    LOG.info("Invoked invokeExternalAndHandleFailure");

    var ctx = restateContext();

    String finalMessage = "begin";

    Awakeable<byte[]> awakeable = ctx.awakeable(TypeTag.BYTES);

    try {
      ctx.sideEffect(
          () -> {
            try {
              NumberSortHttpServerUtils.sendSortNumbersRequest(
                  awakeable.id(), Arrays.asList(3, 2, 1));
            } catch (Exception e) {
              throw new RuntimeException(
                  "Something went wrong while trying to invoke the external http server", e);
            }
            throw new IllegalStateException("external_call");
          });

      awakeable.await();
    } catch (StatusRuntimeException e) {
      finalMessage = finalMessage + ":" + e.getStatus().getDescription();
    }

    try {
      ctx.call(
              FailingServiceGrpc.getFailMethod(),
              ErrorMessage.newBuilder()
                  .setKey(ctx.sideEffect(TypeTag.STRING_UTF8, () -> UUID.randomUUID().toString()))
                  .setErrorMessage("internal_call")
                  .build())
          .await();
    } catch (StatusRuntimeException e) {
      finalMessage = finalMessage + ":" + e.getStatus().getDescription();
    }

    responseObserver.onNext(ErrorMessage.newBuilder().setErrorMessage(finalMessage).build());
    responseObserver.onCompleted();
  }

  @Override
  public void handleNotFound(FailRequest request, StreamObserver<ErrorMessage> responseObserver) {
    var methodDescriptor =
        FailingServiceGrpc.getFailMethod().toBuilder()
            .setFullMethodName(
                MethodDescriptor.generateFullMethodName(
                    FailingServiceGrpc.SERVICE_NAME, "UnknownFn"))
            .build();
    try {
      restateContext().call(methodDescriptor, ErrorMessage.getDefaultInstance()).await();
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode().value() == Code.NOT_FOUND_VALUE) {
        responseObserver.onNext(ErrorMessage.newBuilder().setErrorMessage("notfound").build());
        responseObserver.onCompleted();
      } else {
        throw new IllegalStateException("Got unexpected error: " + e.getMessage());
      }
    }

    throw new IllegalStateException("This should be unreachable");
  }
}

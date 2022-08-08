package dev.restate.e2e.functions.errors;

import com.google.protobuf.Empty;
import com.google.rpc.Code;
import dev.restate.e2e.functions.utils.NumberSortHttpServerUtils;
import dev.restate.sdk.RestateContext;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FailingService extends FailingServiceGrpc.FailingServiceImplBase {

  private static final Logger LOG = LogManager.getLogger(FailingService.class);

  @Override
  public void fail(ErrorMessage request, StreamObserver<Empty> responseObserver) {
    LOG.info("Invoked fail");

    throw Status.UNKNOWN.withDescription(request.getErrorMessage()).asRuntimeException();
  }

  @Override
  public void failAndHandle(ErrorMessage request, StreamObserver<ErrorMessage> responseObserver) {
    var ctx = RestateContext.current();
    LOG.info("Invoked failAndHandle");

    try {
      ctx.call(
              FailingServiceGrpc.getFailMethod(),
              request.toBuilder()
                  .setKey(ctx.sideEffect(String.class, () -> UUID.randomUUID().toString()))
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

    var ctx = RestateContext.current();

    String finalMessage = "begin";

    try {
      ctx.callback(
              byte[].class,
              replyId -> {
                try {
                  NumberSortHttpServerUtils.sendSortNumbersRequest(replyId, Arrays.asList(3, 2, 1));
                } catch (Exception e) {
                  throw new RuntimeException(
                      "Something went wrong while trying to invoke the external http server", e);
                }
                throw new IllegalStateException("external_call");
              })
          .await();
    } catch (StatusRuntimeException e) {
      finalMessage = finalMessage + ":" + e.getStatus().getDescription();
    }

    try {
      ctx.call(
              FailingServiceGrpc.getFailMethod(),
              ErrorMessage.newBuilder()
                  .setKey(ctx.sideEffect(String.class, () -> UUID.randomUUID().toString()))
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
      RestateContext.current().call(methodDescriptor, ErrorMessage.getDefaultInstance()).await();
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode().value() == Code.NOT_FOUND_VALUE) {
        responseObserver.onNext(ErrorMessage.newBuilder().setErrorMessage("notfound").build());
        responseObserver.onCompleted();
      }
    }

    throw new IllegalStateException("This should be unreachable");
  }
}

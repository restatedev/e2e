package dev.restate.e2e.services.errors;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.errors.ErrorsProto.ErrorMessage;
import dev.restate.e2e.services.errors.ErrorsProto.FailRequest;
import dev.restate.e2e.services.utils.NumberSortHttpServerUtils;
import dev.restate.sdk.blocking.Awakeable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.CoreSerdes;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FailingService extends FailingServiceGrpc.FailingServiceImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(FailingService.class);

  private final AtomicInteger eventualSuccessCalls = new AtomicInteger(0);
  private final AtomicInteger eventualSuccessSideEffectCalls = new AtomicInteger(0);

  @Override
  public void terminallyFailingCall(ErrorMessage request, StreamObserver<Empty> responseObserver) {
    LOG.info("Invoked fail");

    throw Status.INTERNAL.withDescription(request.getErrorMessage()).asRuntimeException();
  }

  @Override
  public void callTerminallyFailingCall(
      ErrorMessage request, StreamObserver<ErrorMessage> responseObserver) {
    var ctx = restateContext();
    LOG.info("Invoked failAndHandle");

    ctx.call(
            FailingServiceGrpc.getTerminallyFailingCallMethod(),
            request.toBuilder()
                .setKey(ctx.sideEffect(CoreSerdes.STRING_UTF8, () -> UUID.randomUUID().toString()))
                .build())
        .await();

    throw new IllegalStateException("This should be unreachable");
  }

  @Override
  public void invokeExternalAndHandleFailure(
      FailRequest request, StreamObserver<ErrorMessage> responseObserver) {
    LOG.info("Invoked invokeExternalAndHandleFailure");

    var ctx = restateContext();

    String finalMessage = "begin";

    Awakeable<byte[]> awakeable = ctx.awakeable(CoreSerdes.BYTES);

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
            throw Status.INTERNAL.withDescription("external_call").asRuntimeException();
          });

      awakeable.await();
    } catch (StatusRuntimeException e) {
      finalMessage = finalMessage + ":" + e.getStatus().getDescription();
    }

    try {
      ctx.call(
              FailingServiceGrpc.getTerminallyFailingCallMethod(),
              ErrorMessage.newBuilder()
                  .setKey(
                      ctx.sideEffect(CoreSerdes.STRING_UTF8, () -> UUID.randomUUID().toString()))
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
  public void failingCallWithEventualSuccess(
      ErrorsProto.Request request, StreamObserver<ErrorsProto.AttemptResponse> responseObserver) {
    final int currentAttempt = this.eventualSuccessCalls.incrementAndGet();

    if (currentAttempt >= 4) {
      this.eventualSuccessCalls.set(0);
      responseObserver.onNext(
          ErrorsProto.AttemptResponse.newBuilder().setAttempts(currentAttempt).build());
      responseObserver.onCompleted();
    } else {
      throw new IllegalArgumentException("Failed at attempt: " + currentAttempt);
    }
  }

  @Override
  public void failingSideEffectWithEventualSuccess(
      ErrorsProto.Request request, StreamObserver<ErrorsProto.AttemptResponse> responseObserver) {
    final int successAttempt =
        restateContext()
            .sideEffect(
                CoreSerdes.INT,
                () -> {
                  final int currentAttempt = this.eventualSuccessSideEffectCalls.incrementAndGet();

                  if (currentAttempt >= 4) {
                    this.eventualSuccessSideEffectCalls.set(0);
                    return currentAttempt;
                  } else {
                    throw new IllegalArgumentException("Failed at attempt: " + currentAttempt);
                  }
                });

    responseObserver.onNext(
        ErrorsProto.AttemptResponse.newBuilder().setAttempts(successAttempt).build());
    responseObserver.onCompleted();
  }

  @Override
  public void terminallyFailingSideEffect(
      ErrorMessage request, StreamObserver<Empty> responseObserver) {
    restateContext()
        .sideEffect(
            () -> {
              throw Status.INTERNAL.withDescription(request.getErrorMessage()).asRuntimeException();
            });

    throw new IllegalStateException("Should not be reached.");
  }

  @Override
  public void failingSideEffectWithFiniteRetryPolicy(
      ErrorMessage request, StreamObserver<Empty> responseObserver) {
    throw new UnsupportedOperationException(
        "The Java SDK does not support side effects with a finite retry policy.");
  }
}

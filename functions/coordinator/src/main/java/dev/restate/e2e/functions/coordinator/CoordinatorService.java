package dev.restate.e2e.functions.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.collections.list.AppendRequest;
import dev.restate.e2e.functions.collections.list.ListServiceGrpc;
import dev.restate.e2e.functions.receiver.GetValueRequest;
import dev.restate.e2e.functions.receiver.PingRequest;
import dev.restate.e2e.functions.receiver.ReceiverGrpc;
import dev.restate.e2e.functions.receiver.SetValueRequest;
import dev.restate.sdk.Awaitable;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoordinatorService extends CoordinatorGrpc.CoordinatorImplBase {
  private static final Logger LOG = LogManager.getLogger(CoordinatorService.class);

  @Override
  public void sleep(Duration request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = RestateContext.current();

    LOG.info("Putting service to sleep for {} ms", request.getMillis());

    ctx.sleep(java.time.Duration.ofMillis(request.getMillis()));

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void proxy(Empty request, StreamObserver<ProxyResponse> responseObserver) {
    RestateContext ctx = RestateContext.current();

    var pong =
        ctx.call(
                ReceiverGrpc.getPingMethod(),
                PingRequest.newBuilder()
                    .setKey(ctx.sideEffect(String.class, () -> UUID.randomUUID().toString()))
                    .build())
            .await();

    responseObserver.onNext(ProxyResponse.newBuilder().setMessage(pong.getMessage()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void complex(ComplexRequest request, StreamObserver<ComplexResponse> responseObserver) {
    var ctx = RestateContext.current();

    LOG.info(
        "Starting complex coordination by sleeping for {} ms",
        request.getSleepDuration().getMillis());

    ctx.sleep(java.time.Duration.ofMillis(request.getSleepDuration().getMillis()));

    var receiverUUID = ctx.sideEffect(String.class, () -> UUID.randomUUID().toString());

    LOG.info("Send fire and forget call to {}", ReceiverGrpc.getServiceDescriptor().getName());
    // Functions should be invoked in the same order they were called. This means that
    // background calls as well as request-response calls have an absolute ordering that is defined
    // by their call order. In this concrete case, setValue is guaranteed to be executed before
    // getValue.
    ctx.backgroundCall(
        ReceiverGrpc.getSetValueMethod(),
        SetValueRequest.newBuilder()
            .setKey(receiverUUID)
            .setValue(request.getRequestValue())
            .build());

    LOG.info("Get current value from {}", ReceiverGrpc.getServiceDescriptor().getName());
    var response =
        ctx.call(
                ReceiverGrpc.getGetValueMethod(),
                GetValueRequest.newBuilder().setKey(receiverUUID).build())
            .await();

    LOG.info("Finish complex coordination with response value '{}'", response.getValue());
    responseObserver.onNext(
        ComplexResponse.newBuilder().setResponseValue(response.getValue()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void timeout(Duration request, StreamObserver<TimeoutResponse> responseObserver) {
    RestateContext ctx = RestateContext.current();

    var timeoutOccurred = false;

    var sleepAwaitable = ctx.callback(Void.TYPE, ignored -> {});

    try {
      sleepAwaitable.await(java.time.Duration.ofMillis(request.getMillis()));
    } catch (TimeoutException te) {
      timeoutOccurred = true;
    }

    responseObserver.onNext(
        TimeoutResponse.newBuilder().setTimeoutOccurred(timeoutOccurred).build());
    responseObserver.onCompleted();
  }

  @Override
  public void invokeSequentially(
      InvokeSequentiallyRequest request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = RestateContext.current();

    List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

    for (int i = 0; i < request.getExecuteAsBackgroundCallCount(); i++) {
      if (request.getExecuteAsBackgroundCall(i)) {
        ctx.backgroundCall(
            ListServiceGrpc.getAppendMethod(),
            AppendRequest.newBuilder()
                .setListName("invokeSequentially")
                .setValue(String.valueOf(i))
                .build());
      } else {
        collectedAwaitables.add(
            ctx.call(
                ListServiceGrpc.getAppendMethod(),
                AppendRequest.newBuilder()
                    .setListName("invokeSequentially")
                    .setValue(String.valueOf(i))
                    .build()));
      }
    }

    Awaitable.all(collectedAwaitables).await();

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void invokeSideEffects(
      InvokeSideEffectsRequest request, StreamObserver<InvokeSideEffectsResult> responseObserver) {
    RestateContext ctx = RestateContext.current();

    AtomicInteger invokedSideEffects = new AtomicInteger(0);

    ctx.sideEffect(() -> invokedSideEffects.incrementAndGet());
    ctx.sideEffect(() -> invokedSideEffects.incrementAndGet());
    if (request.getLastIsCallback()) {
      ctx.callback(Void.TYPE, ignored -> invokedSideEffects.incrementAndGet());
    } else {
      ctx.sideEffect(() -> invokedSideEffects.incrementAndGet());
    }

    responseObserver.onNext(
        InvokeSideEffectsResult.newBuilder()
            .setInvokedTimes(invokedSideEffects.intValue())
            .build());
    responseObserver.onCompleted();
  }
}
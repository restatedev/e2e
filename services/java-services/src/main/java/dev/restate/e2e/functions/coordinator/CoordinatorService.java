package dev.restate.e2e.functions.coordinator;

import static dev.restate.e2e.functions.coordinator.CoordinatorProto.*;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.collections.list.ListProto.AppendRequest;
import dev.restate.e2e.functions.collections.list.ListServiceGrpc;
import dev.restate.e2e.functions.receiver.ReceiverGrpc;
import dev.restate.e2e.functions.receiver.ReceiverProto.GetValueRequest;
import dev.restate.e2e.functions.receiver.ReceiverProto.PingRequest;
import dev.restate.e2e.functions.receiver.ReceiverProto.SetValueRequest;
import dev.restate.sdk.blocking.Awaitable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.TypeTag;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoordinatorService extends CoordinatorGrpc.CoordinatorImplBase
    implements RestateBlockingService {
  private static final Logger LOG = LogManager.getLogger(CoordinatorService.class);

  @Override
  public void sleep(Duration request, StreamObserver<Empty> responseObserver) {
    manyTimers(ManyTimersRequest.newBuilder().addTimer(request).build(), responseObserver);
  }

  @Override
  public void manyTimers(ManyTimersRequest request, StreamObserver<Empty> responseObserver) {
    LOG.info("many timers {}", request.getTimerList());

    awaitableAll(
        request.getTimerList().stream()
            .map(d -> restateContext().timer(java.time.Duration.ofMillis(d.getMillis())))
            .collect(Collectors.toList()));

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void proxy(Empty request, StreamObserver<ProxyResponse> responseObserver) {
    RestateContext ctx = restateContext();

    String key = ctx.sideEffect(TypeTag.STRING_UTF8, () -> UUID.randomUUID().toString());

    var pong =
        ctx.call(ReceiverGrpc.getPingMethod(), PingRequest.newBuilder().setKey(key).build())
            .await();

    responseObserver.onNext(ProxyResponse.newBuilder().setMessage(pong.getMessage()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void complex(ComplexRequest request, StreamObserver<ComplexResponse> responseObserver) {
    RestateContext ctx = restateContext();

    LOG.info(
        "Starting complex coordination by sleeping for {} ms",
        request.getSleepDuration().getMillis());

    ctx.sleep(java.time.Duration.ofMillis(request.getSleepDuration().getMillis()));

    var receiverUUID = ctx.sideEffect(TypeTag.STRING_UTF8, () -> UUID.randomUUID().toString());

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
    var timeoutOccurred = false;

    var awakeable = restateContext().awakeable(TypeTag.VOID);
    try {
      awakeable.await(java.time.Duration.ofMillis(request.getMillis()));
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
    RestateContext ctx = restateContext();

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

    awaitableAll(collectedAwaitables);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  private static void awaitableAll(List<Awaitable<?>> awaitables) {
    if (awaitables.size() == 1) {
      awaitables.get(0).await();
    } else if (awaitables.size() == 2) {
      Awaitable.all(awaitables.get(0), awaitables.get(1)).await();
    } else if (awaitables.size() >= 2) {
      Awaitable.all(
              awaitables.get(0),
              awaitables.get(1),
              awaitables.subList(2, awaitables.size()).toArray(Awaitable[]::new))
          .await();
    }
  }
}

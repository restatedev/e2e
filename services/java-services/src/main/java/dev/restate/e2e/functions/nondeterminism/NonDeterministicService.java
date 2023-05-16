package dev.restate.e2e.functions.nondeterminism;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.counter.CounterGrpc;
import dev.restate.e2e.functions.counter.CounterProto.CounterAddRequest;
import dev.restate.e2e.functions.counter.CounterProto.CounterRequest;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NonDeterministicService
    extends NonDeterministicServiceGrpc.NonDeterministicServiceImplBase
    implements RestateBlockingService {

  private final Map<String, Integer> invocationCounts = new ConcurrentHashMap<>();
  private final StateKey<String> STATE_A = StateKey.of("a", TypeTag.STRING_UTF8);
  private final StateKey<String> STATE_B = StateKey.of("b", TypeTag.STRING_UTF8);

  @Override
  public void leftSleepRightCall(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    if (doLeftAction(request)) {
      restateContext().sleep(Duration.ofMillis(100));
    } else {
      restateContext()
          .call(
              CounterGrpc.getGetMethod(), CounterRequest.newBuilder().setCounterName("abc").build())
          .await();
    }
    incrementCounterAndEnd(request, responseObserver);
  }

  @Override
  public void callDifferentMethod(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    if (doLeftAction(request)) {
      restateContext()
          .call(
              CounterGrpc.getGetMethod(), CounterRequest.newBuilder().setCounterName("abc").build())
          .await();
    } else {
      restateContext()
          .call(
              CounterGrpc.getResetMethod(),
              CounterRequest.newBuilder().setCounterName("abc").build())
          .await();
    }
    incrementCounterAndEnd(request, responseObserver);
  }

  @Override
  public void backgroundInvokeWithDifferentTargets(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    if (doLeftAction(request)) {
      restateContext()
          .backgroundCall(
              CounterGrpc.getGetMethod(),
              CounterRequest.newBuilder().setCounterName("abc").build());
    } else {
      restateContext()
          .backgroundCall(
              CounterGrpc.getResetMethod(),
              CounterRequest.newBuilder().setCounterName("abc").build());
    }
    restateContext().sleep(Duration.ofMillis(100));
    incrementCounterAndEnd(request, responseObserver);
  }

  @Override
  public void setDifferentKey(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    if (doLeftAction(request)) {
      restateContext().set(STATE_A, "my-state");
    } else {
      restateContext().set(STATE_B, "my-state");
    }
    restateContext().sleep(Duration.ofMillis(100));
    incrementCounterAndEnd(request, responseObserver);
  }

  @Override
  public void setDifferentValue(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    restateContext().set(STATE_A, UUID.randomUUID().toString());
    restateContext().sleep(Duration.ofMillis(100));
    incrementCounterAndEnd(request, responseObserver);
  }

  private boolean doLeftAction(NonDeterminismProto.NonDeterministicRequest request) {
    return invocationCounts.merge(request.getKey(), 1, Integer::sum) % 2 == 1;
  }

  private void incrementCounterAndEnd(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    restateContext()
        .backgroundCall(
            CounterGrpc.getAddMethod(),
            CounterAddRequest.newBuilder().setCounterName(request.getKey()).setValue(1).build());
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.nondeterminism;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.counter.CounterGrpc;
import dev.restate.e2e.services.counter.CounterProto.CounterAddRequest;
import dev.restate.e2e.services.counter.CounterProto.CounterRequest;
import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.RestateService;
import dev.restate.sdk.common.StateKey;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NonDeterministicService
    extends NonDeterministicServiceGrpc.NonDeterministicServiceImplBase implements RestateService {

  private final Map<String, Integer> invocationCounts = new ConcurrentHashMap<>();
  private final StateKey<String> STATE_A = StateKey.string("a");
  private final StateKey<String> STATE_B = StateKey.string("b");

  @Override
  public void leftSleepRightCall(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    if (doLeftAction(request)) {
      KeyedContext.current().sleep(Duration.ofMillis(100));
    } else {
      KeyedContext.current()
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
      KeyedContext.current()
          .call(
              CounterGrpc.getGetMethod(), CounterRequest.newBuilder().setCounterName("abc").build())
          .await();
    } else {
      KeyedContext.current()
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
      KeyedContext.current()
          .oneWayCall(
              CounterGrpc.getGetMethod(),
              CounterRequest.newBuilder().setCounterName("abc").build());
    } else {
      KeyedContext.current()
          .oneWayCall(
              CounterGrpc.getResetMethod(),
              CounterRequest.newBuilder().setCounterName("abc").build());
    }
    KeyedContext.current().sleep(Duration.ofMillis(100));
    incrementCounterAndEnd(request, responseObserver);
  }

  @Override
  public void setDifferentKey(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    if (doLeftAction(request)) {
      KeyedContext.current().set(STATE_A, "my-state");
    } else {
      KeyedContext.current().set(STATE_B, "my-state");
    }
    KeyedContext.current().sleep(Duration.ofMillis(100));
    incrementCounterAndEnd(request, responseObserver);
  }

  private boolean doLeftAction(NonDeterminismProto.NonDeterministicRequest request) {
    return invocationCounts.merge(request.getKey(), 1, Integer::sum) % 2 == 1;
  }

  private void incrementCounterAndEnd(
      NonDeterminismProto.NonDeterministicRequest request, StreamObserver<Empty> responseObserver) {
    KeyedContext.current()
        .oneWayCall(
            CounterGrpc.getAddMethod(),
            CounterAddRequest.newBuilder().setCounterName(request.getKey()).setValue(1).build());
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

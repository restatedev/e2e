// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.counter;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc;
import dev.restate.e2e.services.singletoncounter.SingletonCounterProto.CounterNumber;
import dev.restate.sdk.RestateBlockingService;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SingletonCounterService extends SingletonCounterGrpc.SingletonCounterImplBase
    implements RestateBlockingService {

  private static final Logger logger = LogManager.getLogger(SingletonCounterService.class);

  private static final StateKey<Long> COUNTER_KEY = StateKey.of("counter", CoreSerdes.LONG);

  @Override
  public void reset(Empty request, StreamObserver<Empty> responseObserver) {
    var ctx = restateContext();

    logger.info("Counter cleaned up");

    ctx.clear(COUNTER_KEY);

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void add(CounterNumber request, StreamObserver<Empty> responseObserver) {
    var ctx = restateContext();

    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Old counter value: {}", counter);

    counter += request.getValue();
    ctx.set(COUNTER_KEY, counter);

    logger.info("New counter value: {}", counter);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void get(Empty request, StreamObserver<CounterNumber> responseObserver) {
    var ctx = restateContext();

    CounterNumber result =
        CounterNumber.newBuilder().setValue(ctx.get(COUNTER_KEY).orElse(0L)).build();

    responseObserver.onNext(result);
    responseObserver.onCompleted();
  }
}

package dev.restate.e2e.functions.counter;

import com.google.protobuf.Empty;
import dev.restate.sdk.RestateContext;
import dev.restate.sdk.StateKey;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CounterService extends CounterGrpc.CounterImplBase {

  private static final Logger logger = LogManager.getLogger(CounterService.class);

  private static final StateKey<Long> COUNTER_KEY = StateKey.of("counter", Long.TYPE);

  @Override
  public void reset(Empty request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = RestateContext.current();

    logger.info("Counter cleaned up");

    ctx.clear(COUNTER_KEY);

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void add(Number request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = RestateContext.current();

    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Old counter value: {}", counter);

    counter += request.getValue();
    ctx.set(COUNTER_KEY, counter);

    logger.info("New counter value: {}", counter);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void get(Empty request, StreamObserver<Number> responseObserver) {
    RestateContext ctx = RestateContext.current();

    Number result = Number.newBuilder().setValue(ctx.get(COUNTER_KEY).orElse(0L)).build();

    responseObserver.onNext(result);
    responseObserver.onCompleted();
  }

  @Override
  public void getAndAdd(Number request, StreamObserver<CounterUpdateResult> responseObserver) {
    RestateContext ctx = RestateContext.current();

    long oldCount = ctx.get(COUNTER_KEY).orElse(0L);
    long newCount = oldCount + request.getValue();
    ctx.set(COUNTER_KEY, newCount);

    logger.info("Old counter value: {}", oldCount);
    logger.info("New counter value: {}", newCount);

    responseObserver.onNext(
        CounterUpdateResult.newBuilder().setOldValue(oldCount).setNewValue(newCount).build());
    responseObserver.onCompleted();
  }
}

package dev.restate.e2e.functions.counter;

import static dev.restate.e2e.functions.counter.CounterProto.*;

import com.google.protobuf.Empty;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.StateKey;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CounterService extends CounterGrpc.CounterImplBase implements RestateBlockingService {

  private static final Logger logger = LogManager.getLogger(CounterService.class);

  private static final StateKey<Long> COUNTER_KEY = StateKey.of("counter", Long.TYPE);

  @Override
  public void reset(CounterRequest request, StreamObserver<Empty> responseObserver) {
    var ctx = restateContext();

    logger.info("Counter '{}' cleaned up", request.getCounterName());

    ctx.clear(COUNTER_KEY);

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void add(CounterAddRequest request, StreamObserver<Empty> responseObserver) {
    var ctx = restateContext();

    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Old counter '{}' value: {}", request.getCounterName(), counter);

    counter += request.getValue();
    ctx.set(COUNTER_KEY, counter);

    logger.info("New counter '{}' value: {}", request.getCounterName(), counter);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void addThenFail(CounterAddRequest request, StreamObserver<Empty> responseObserver) {
    var ctx = restateContext();

    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Old counter value: {}", counter);

    counter += request.getValue();
    ctx.set(COUNTER_KEY, counter);

    logger.info("New counter value: {}", counter);

    throw new IllegalStateException(request.getCounterName());
  }

  @Override
  public void get(CounterRequest request, StreamObserver<GetResponse> responseObserver) {
    var ctx = restateContext();

    GetResponse result = GetResponse.newBuilder().setValue(ctx.get(COUNTER_KEY).orElse(0L)).build();

    responseObserver.onNext(result);
    responseObserver.onCompleted();
  }

  @Override
  public void getAndAdd(
      CounterAddRequest request, StreamObserver<CounterUpdateResult> responseObserver) {
    var ctx = restateContext();

    long oldCount = ctx.get(COUNTER_KEY).orElse(0L);
    long newCount = oldCount + request.getValue();
    ctx.set(COUNTER_KEY, newCount);

    logger.info("Old counter '{}' value: {}", request.getCounterName(), oldCount);
    logger.info("New counter '{}' value: {}", request.getCounterName(), newCount);

    responseObserver.onNext(
        CounterUpdateResult.newBuilder().setOldValue(oldCount).setNewValue(newCount).build());
    responseObserver.onCompleted();
  }
}

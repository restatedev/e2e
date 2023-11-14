package dev.restate.e2e.services.counter;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc;
import dev.restate.e2e.services.singletoncounter.SingletonCounterProto.CounterNumber;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.StateKey;
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

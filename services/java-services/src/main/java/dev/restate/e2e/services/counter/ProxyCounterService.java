package dev.restate.e2e.services.counter;

import static dev.restate.e2e.services.counter.CounterProto.CounterAddRequest;

import com.google.protobuf.Empty;
import dev.restate.sdk.blocking.RestateBlockingService;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyCounterService extends ProxyCounterGrpc.ProxyCounterImplBase
    implements RestateBlockingService {

  private static final Logger logger = LogManager.getLogger(ProxyCounterService.class);

  @Override
  public void addInBackground(CounterAddRequest request, StreamObserver<Empty> responseObserver) {
    logger.info("addInBackground invoked {}", request);

    // Increment the counter
    restateContext().backgroundCall(CounterGrpc.getAddMethod(), request);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

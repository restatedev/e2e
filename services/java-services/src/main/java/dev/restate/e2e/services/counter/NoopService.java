package dev.restate.e2e.services.counter;

import static dev.restate.e2e.services.counter.CounterProto.CounterAddRequest;

import com.google.protobuf.Empty;
import dev.restate.sdk.blocking.RestateBlockingService;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NoopService extends NoopGrpc.NoopImplBase implements RestateBlockingService {

  private static final Logger logger = LogManager.getLogger(NoopService.class);

  @Override
  public void doAndReportInvocationCount(Empty request, StreamObserver<Empty> responseObserver) {
    logger.info("doAndReportInvocationCount invoked");

    var ctx = restateContext();

    // Increment the counter
    ctx.backgroundCall(
        CounterGrpc.getAddMethod(),
        CounterAddRequest.newBuilder()
            .setCounterName("doAndReportInvocationCount")
            .setValue(1)
            .build());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

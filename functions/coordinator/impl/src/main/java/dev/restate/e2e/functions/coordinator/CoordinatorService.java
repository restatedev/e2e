package dev.restate.e2e.functions.coordinator;

import com.google.protobuf.Empty;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoordinatorService extends CoordinatorGrpc.CoordinatorImplBase {
  private static final Logger LOG = LogManager.getLogger(CoordinatorService.class);

  @Override
  public void sleep(Duration request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = RestateContext.current();

    LOG.info(
        "Putting service {} to sleep for {} ms", ctx.id().orElse("unknown"), request.getMillis());

    ctx.sleep(java.time.Duration.ofMillis(request.getMillis()));

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }
}

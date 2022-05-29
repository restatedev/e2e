package dev.restate.e2e.functions.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.receiver.ReceiverGrpc;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoordinatorService extends CoordinatorGrpc.CoordinatorImplBase {
  private static final Logger LOG = LogManager.getLogger(CoordinatorService.class);

  @Override
  public void sleep(Duration request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = RestateContext.current();

    LOG.info("Putting service {} to sleep for {} ms", ctx.key(), request.getMillis());

    ctx.sleep(java.time.Duration.ofMillis(request.getMillis()));

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void proxy(Empty request, StreamObserver<ProxyResponse> responseObserver) {
    RestateContext ctx = RestateContext.current();

    var receiverBlockingStub = ReceiverGrpc.newBlockingStub(ctx.channel());

    var pong = receiverBlockingStub.ping(Empty.newBuilder().build());

    responseObserver.onNext(ProxyResponse.newBuilder().setMessage(pong.getMessage()).build());
    responseObserver.onCompleted();
  }
}

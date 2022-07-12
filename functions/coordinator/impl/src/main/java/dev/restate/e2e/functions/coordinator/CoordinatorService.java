package dev.restate.e2e.functions.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.receiver.ReceiverGrpc;
import dev.restate.e2e.functions.receiver.SetValueRequest;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeoutException;
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

    var pong = ctx.call(ReceiverGrpc.getPingMethod(), Empty.newBuilder().build()).await();

    responseObserver.onNext(ProxyResponse.newBuilder().setMessage(pong.getMessage()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void complex(ComplexRequest request, StreamObserver<ComplexResponse> responseObserver) {
    var ctx = RestateContext.current();

    LOG.info(
        "Starting complex coordination by sleeping for {} ms",
        request.getSleepDuration().getMillis());

    ctx.sleep(java.time.Duration.ofMillis(request.getSleepDuration().getMillis()));

    LOG.info("Send fire and forget call to {}", ReceiverGrpc.getServiceDescriptor().getName());
    // Functions should be invoked in the same order they were called. This means that
    // fire-and-forget calls as well as coordinator calls have an absolute ordering that is defined
    // by their call order. In this concrete case, setValue is guaranteed to be executed before
    // getValue.
    // TODO this only works atm because fire-and-forget messages are sent first.
    //  See https://github.com/restatedev/java-sdk/issues/32 for more details
    ctx.backgroundCall(
        ReceiverGrpc.getSetValueMethod(),
        SetValueRequest.newBuilder().setValue(request.getRequestValue()).build());

    LOG.info("Get current value from {}", ReceiverGrpc.getServiceDescriptor().getName());
    var response = ctx.call(ReceiverGrpc.getGetValueMethod(), Empty.getDefaultInstance()).await();

    LOG.info("Finish complex coordination with response value '{}'", response.getValue());
    responseObserver.onNext(
        ComplexResponse.newBuilder().setResponseValue(response.getValue()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void timeout(Duration request, StreamObserver<TimeoutResponse> responseObserver) {
    RestateContext ctx = RestateContext.current();

    var timeoutOccurred = false;

    var sleepAwaitable = ctx.callback(Void.TYPE, ignored -> {});

    try {
      sleepAwaitable.await(java.time.Duration.ofMillis(request.getMillis()));
    } catch (TimeoutException te) {
      timeoutOccurred = true;
    }

    responseObserver.onNext(
        TimeoutResponse.newBuilder().setTimeoutOccurred(timeoutOccurred).build());
    responseObserver.onCompleted();
  }
}

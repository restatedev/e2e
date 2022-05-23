package dev.restate.e2e.functions.counter;

import com.google.protobuf.Empty;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NoopService extends NoopGrpc.NoopImplBase {

    private static final Logger logger = LogManager.getLogger(NoopService.class);

    @Override
    public void doAndReportInvocationCount(Empty request, StreamObserver<Empty> responseObserver) {
        logger.info("doAndReportInvocationCount invoked");

        RestateContext ctx = RestateContext.current();

        // Increment the counter
        CounterGrpc.CounterBlockingStub stub = CounterGrpc
                .newBlockingStub(ctx.channel("doAndReportInvocationCount")).withCompression("identity");

        ctx.asyncDefer(() -> stub.add(Number.newBuilder().setValue(1).build()));

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
package dev.restate.e2e.services.sideeffect;

import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;

public class SideEffectService extends SideEffectGrpc.SideEffectImplBase
    implements RestateBlockingService {

  @Override
  public void invokeSideEffects(
      SideEffectProto.InvokeSideEffectsRequest request,
      StreamObserver<SideEffectProto.InvokeSideEffectsResult> responseObserver) {
    RestateContext ctx = restateContext();

    AtomicInteger invokedSideEffects = new AtomicInteger(0);

    ctx.sideEffect(() -> invokedSideEffects.incrementAndGet());
    ctx.sideEffect(() -> invokedSideEffects.incrementAndGet());
    ctx.sideEffect(() -> invokedSideEffects.incrementAndGet());

    responseObserver.onNext(
        SideEffectProto.InvokeSideEffectsResult.newBuilder()
            .setNonDeterministicInvocationCount(invokedSideEffects.intValue())
            .build());
    responseObserver.onCompleted();
  }
}

// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.sideeffect;

import dev.restate.sdk.RestateBlockingService;
import dev.restate.sdk.RestateContext;
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

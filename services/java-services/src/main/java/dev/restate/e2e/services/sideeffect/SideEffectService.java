// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.sideeffect;

import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.RestateService;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;

public class SideEffectService extends SideEffectGrpc.SideEffectImplBase implements RestateService {

  @Override
  public void invokeSideEffects(
      SideEffectProto.InvokeSideEffectsRequest request,
      StreamObserver<SideEffectProto.InvokeSideEffectsResult> responseObserver) {
    KeyedContext ctx = KeyedContext.current();

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

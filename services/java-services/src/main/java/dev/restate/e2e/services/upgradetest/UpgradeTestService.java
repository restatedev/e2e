// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.upgradetest;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.awakeableholder.AwakeableHolderProto;
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceGrpc;
import dev.restate.e2e.services.collections.list.ListProto;
import dev.restate.e2e.services.collections.list.ListServiceGrpc;
import dev.restate.e2e.services.upgradetest.UpgradeTestProto.Result;
import dev.restate.sdk.Awakeable;
import dev.restate.sdk.RestateBlockingService;
import dev.restate.sdk.RestateContext;
import dev.restate.sdk.common.CoreSerdes;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

public class UpgradeTestService extends UpgradeTestServiceGrpc.UpgradeTestServiceImplBase
    implements RestateBlockingService {

  // Value should be either "v1" or "v2"
  private final String version =
      Objects.requireNonNull(System.getenv("E2E_UPGRADETEST_VERSION")).trim();

  @Override
  public void executeSimple(Empty request, StreamObserver<Result> responseObserver) {
    responseObserver.onNext(Result.newBuilder().setMessage(version).build());
    responseObserver.onCompleted();
  }

  @Override
  public void executeComplex(Empty request, StreamObserver<Result> responseObserver) {
    RestateContext ctx = restateContext();

    if (!"v1".equals(version)) {
      throw new IllegalStateException(
          "executeComplex should not be invoked with version different from 1!");
    }

    // In v1 case we create an awakeable, we ask the AwakeableHolderService to hold it, and then we
    // await on it
    Awakeable<String> awakeable = ctx.awakeable(CoreSerdes.STRING_UTF8);
    ctx.oneWayCall(
        AwakeableHolderServiceGrpc.getHoldMethod(),
        AwakeableHolderProto.HoldRequest.newBuilder()
            .setName("upgrade")
            .setId(awakeable.id())
            .build());
    awakeable.await();

    // Store the result in List service, because this service is invoked with
    // dev.restate.Ingress#Invoke
    ctx.oneWayCall(
        ListServiceGrpc.getAppendMethod(),
        ListProto.AppendRequest.newBuilder().setListName("upgrade-test").setValue(version).build());

    responseObserver.onNext(Result.newBuilder().setMessage(version).build());
    responseObserver.onCompleted();
  }
}

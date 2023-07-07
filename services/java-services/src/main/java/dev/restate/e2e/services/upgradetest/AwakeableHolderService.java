package dev.restate.e2e.services.upgradetest;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.upgradetest.UpgradeTestProto.HasAwakeableResponse;
import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.blocking.RestateContext;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import io.grpc.stub.StreamObserver;

public class AwakeableHolderService
    extends AwakeableHolderServiceGrpc.AwakeableHolderServiceImplBase
    implements RestateBlockingService {

  private final StateKey<AwakeableIdentifier> STATE_ID =
      StateKey.of("identifier", AwakeableIdentifier.class);

  @Override
  public void hold(AwakeableIdentifier request, StreamObserver<Empty> responseObserver) {
    restateContext().set(STATE_ID, request);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void hasAwakeable(Empty request, StreamObserver<HasAwakeableResponse> responseObserver) {
    responseObserver.onNext(
        HasAwakeableResponse.newBuilder()
            .setHasAwakeable(restateContext().get(STATE_ID).isPresent())
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void unlock(Empty request, StreamObserver<Empty> responseObserver) {
    RestateContext ctx = restateContext();

    ctx.awakeableHandle(ctx.get(STATE_ID).get()).complete(TypeTag.STRING_UTF8, "");

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

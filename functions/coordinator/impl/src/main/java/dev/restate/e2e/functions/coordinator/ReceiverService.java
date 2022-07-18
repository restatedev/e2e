package dev.restate.e2e.functions.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.receiver.*;
import dev.restate.sdk.RestateContext;
import dev.restate.sdk.StateKey;
import io.grpc.stub.StreamObserver;

public class ReceiverService extends ReceiverGrpc.ReceiverImplBase {

  public static final StateKey<String> STATE_KEY = StateKey.of("my-state", String.class);

  @Override
  public void ping(PingRequest request, StreamObserver<Pong> responseObserver) {
    responseObserver.onNext(Pong.newBuilder().setMessage("pong").build());
    responseObserver.onCompleted();
  }

  @Override
  public void setValue(SetValueRequest request, StreamObserver<Empty> responseObserver) {
    var ctx = RestateContext.current();

    ctx.set(STATE_KEY, request.getValue());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getValue(GetValueRequest request, StreamObserver<GetValueResponse> responseObserver) {
    var ctx = RestateContext.current();

    var state = ctx.get(STATE_KEY).orElse("");

    responseObserver.onNext(GetValueResponse.newBuilder().setValue(state).build());
    responseObserver.onCompleted();
  }
}

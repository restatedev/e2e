package dev.restate.e2e.functions.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.receiver.GetValueResponse;
import dev.restate.e2e.functions.receiver.Pong;
import dev.restate.e2e.functions.receiver.ReceiverGrpc;
import dev.restate.e2e.functions.receiver.SetValueRequest;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;

public class ReceiverService extends ReceiverGrpc.ReceiverImplBase {

  public static final String STATE_KEY = "my-state";

  @Override
  public void ping(Empty request, StreamObserver<Pong> responseObserver) {
    responseObserver.onNext(Pong.newBuilder().setMessage("pong").build());
    responseObserver.onCompleted();
  }

  @Override
  public void setValue(SetValueRequest request, StreamObserver<Empty> responseObserver) {
    var ctx = RestateContext.current();

    ctx.set(STATE_KEY, request.getValue().getBytes(StandardCharsets.UTF_8));

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getValue(Empty request, StreamObserver<GetValueResponse> responseObserver) {
    var ctx = RestateContext.current();

    var state =
        ctx.get(STATE_KEY).map(bytes -> new String(bytes, StandardCharsets.UTF_8)).orElse("");

    responseObserver.onNext(GetValueResponse.newBuilder().setValue(state).build());
    responseObserver.onCompleted();
  }
}

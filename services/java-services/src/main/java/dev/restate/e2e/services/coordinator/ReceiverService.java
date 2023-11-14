package dev.restate.e2e.services.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.receiver.ReceiverGrpc;
import dev.restate.e2e.services.receiver.ReceiverProto.*;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.StateKey;
import io.grpc.stub.StreamObserver;

public class ReceiverService extends ReceiverGrpc.ReceiverImplBase
    implements RestateBlockingService {

  public static final StateKey<String> STATE_KEY = StateKey.of("my-state", CoreSerdes.STRING_UTF8);

  @Override
  public void ping(PingRequest request, StreamObserver<Pong> responseObserver) {
    responseObserver.onNext(Pong.newBuilder().setMessage("pong").build());
    responseObserver.onCompleted();
  }

  @Override
  public void setValue(SetValueRequest request, StreamObserver<Empty> responseObserver) {
    restateContext().set(STATE_KEY, request.getValue());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getValue(GetValueRequest request, StreamObserver<GetValueResponse> responseObserver) {
    var state = restateContext().get(STATE_KEY).orElse("");

    responseObserver.onNext(GetValueResponse.newBuilder().setValue(state).build());
    responseObserver.onCompleted();
  }
}

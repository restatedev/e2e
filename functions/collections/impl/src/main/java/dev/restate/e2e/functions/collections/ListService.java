package dev.restate.e2e.functions.collections;

import com.google.protobuf.Empty;
import dev.restate.e2e.functions.collections.list.AppendRequest;
import dev.restate.e2e.functions.collections.list.List;
import dev.restate.e2e.functions.collections.list.ListServiceGrpc;
import dev.restate.e2e.functions.collections.list.Request;
import dev.restate.sdk.StateKey;
import io.grpc.stub.StreamObserver;

public class ListService extends ListServiceGrpc.ListServiceImplBase {

  private static final StateKey<List> LIST_KEY = StateKey.of("list", List.class);

  @Override
  public void append(AppendRequest request, StreamObserver<Empty> responseObserver) {
    LIST_KEY.update(
        l -> l.orElse(List.getDefaultInstance()).toBuilder().addValues(request.getValue()).build());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void clear(
      Request request,
      StreamObserver<dev.restate.e2e.functions.collections.list.List> responseObserver) {
    var l = LIST_KEY.get().orElse(List.getDefaultInstance());
    LIST_KEY.clear();

    responseObserver.onNext(l);
    responseObserver.onCompleted();
  }
}

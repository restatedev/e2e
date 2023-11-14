package dev.restate.e2e.services.collections;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.collections.list.ListProto;
import dev.restate.e2e.services.collections.list.ListServiceGrpc;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.CoreSerdes;
import dev.restate.sdk.core.StateKey;
import io.grpc.stub.StreamObserver;

public class ListService extends ListServiceGrpc.ListServiceImplBase
    implements RestateBlockingService {

  private static final StateKey<ListProto.List> LIST_KEY =
      StateKey.of("list", CoreSerdes.ofProtobuf(ListProto.List.parser()));

  @Override
  public void append(ListProto.AppendRequest request, StreamObserver<Empty> responseObserver) {
    ListProto.List list =
        restateContext().get(LIST_KEY).orElse(ListProto.List.getDefaultInstance());
    list = list.toBuilder().addValues(request.getValue()).build();
    restateContext().set(LIST_KEY, list);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void clear(ListProto.Request request, StreamObserver<ListProto.List> responseObserver) {
    ListProto.List list =
        restateContext().get(LIST_KEY).orElse(ListProto.List.getDefaultInstance());
    restateContext().clear(LIST_KEY);

    responseObserver.onNext(list);
    responseObserver.onCompleted();
  }

  @Override
  public void get(ListProto.Request request, StreamObserver<ListProto.List> responseObserver) {
    ListProto.List list =
        restateContext().get(LIST_KEY).orElse(ListProto.List.getDefaultInstance());

    responseObserver.onNext(list);
    responseObserver.onCompleted();
  }
}

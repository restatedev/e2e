package dev.restate.e2e.services.eventhandler;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.counter.CounterGrpc;
import dev.restate.e2e.services.counter.CounterProto;
import dev.restate.generated.Event;
import dev.restate.sdk.blocking.RestateBlockingService;
import io.grpc.stub.StreamObserver;

public class EventHandlerService extends EventHandlerGrpc.EventHandlerImplBase
    implements RestateBlockingService {

  @Override
  public void handle(Event event, StreamObserver<Empty> responseObserver) {
    restateContext()
        .oneWayCall(
            CounterGrpc.getAddMethod(),
            CounterProto.CounterAddRequest.newBuilder()
                .setCounterName(event.getKey().toStringUtf8())
                .setValue(Long.parseLong(event.getPayload().toStringUtf8()))
                .build());
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

package dev.restate.e2e.services.externalcall;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.externalcall.ReplierProto.Reply;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.TypeTag;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReplierService extends ReplierGrpc.ReplierImplBase implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(ReplierService.class);

  @Override
  public void replyToRandomNumberListGenerator(
      Reply request, StreamObserver<Empty> responseObserver) {
    LOG.info("Received request " + request);
    restateContext()
        .awakeableHandle(request.getReplyIdentifier())
        .complete(TypeTag.BYTES, request.getPayload().toByteArray());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

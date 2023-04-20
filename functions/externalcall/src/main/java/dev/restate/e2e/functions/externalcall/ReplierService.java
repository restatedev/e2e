package dev.restate.e2e.functions.externalcall;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.restate.e2e.functions.externalcall.ReplierProto.Reply;
import dev.restate.generated.core.AwakeableIdentifier;
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
    try {
      restateContext()
          .awakeableHandle(
              AwakeableIdentifier.parseFrom(request.getReplyIdentifier().toByteArray()))
          .complete(TypeTag.BYTES, request.getPayload().toByteArray());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

package dev.restate.e2e.functions.externalcall;

import com.google.protobuf.Empty;
import dev.restate.sdk.ReplyIdentifier;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReplierService extends ReplierGrpc.ReplierImplBase {

  private static final Logger LOG = LogManager.getLogger(ReplierService.class);

  @Override
  public void replyToRandomNumberListGenerator(
      Reply request, StreamObserver<Empty> responseObserver) {
    LOG.info("Received request " + request);
    RestateContext.current()
        .reply(
            ReplyIdentifier.fromBytes(request.getReplyIdentifier().toByteArray()),
            request.getPayload().toByteArray());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

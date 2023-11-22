// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.counter;

import static dev.restate.e2e.services.counter.CounterProto.CounterAddRequest;

import com.google.protobuf.Empty;
import dev.restate.sdk.blocking.RestateBlockingService;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyCounterService extends ProxyCounterGrpc.ProxyCounterImplBase
    implements RestateBlockingService {

  private static final Logger logger = LogManager.getLogger(ProxyCounterService.class);

  @Override
  public void addInBackground(CounterAddRequest request, StreamObserver<Empty> responseObserver) {
    logger.info("addInBackground invoked {}", request);

    // Increment the counter
    restateContext().oneWayCall(CounterGrpc.getAddMethod(), request);

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}

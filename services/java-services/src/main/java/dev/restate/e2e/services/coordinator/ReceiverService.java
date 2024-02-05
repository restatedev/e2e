// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.coordinator;

import com.google.protobuf.Empty;
import dev.restate.e2e.services.receiver.ReceiverGrpc;
import dev.restate.e2e.services.receiver.ReceiverProto.*;
import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.RestateService;
import dev.restate.sdk.common.StateKey;
import io.grpc.stub.StreamObserver;

public class ReceiverService extends ReceiverGrpc.ReceiverImplBase implements RestateService {

  public static final StateKey<String> STATE_KEY = StateKey.string("my-state");

  @Override
  public void ping(PingRequest request, StreamObserver<Pong> responseObserver) {
    responseObserver.onNext(Pong.newBuilder().setMessage("pong").build());
    responseObserver.onCompleted();
  }

  @Override
  public void setValue(SetValueRequest request, StreamObserver<Empty> responseObserver) {
    KeyedContext.current().set(STATE_KEY, request.getValue());

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getValue(GetValueRequest request, StreamObserver<GetValueResponse> responseObserver) {
    var state = KeyedContext.current().get(STATE_KEY).orElse("");

    responseObserver.onNext(GetValueResponse.newBuilder().setValue(state).build());
    responseObserver.onCompleted();
  }
}

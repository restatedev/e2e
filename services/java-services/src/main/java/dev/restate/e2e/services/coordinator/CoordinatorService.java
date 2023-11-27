// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.coordinator;

import static dev.restate.e2e.services.coordinator.CoordinatorProto.*;

import dev.restate.e2e.services.collections.list.ListProto.AppendRequest;
import dev.restate.e2e.services.collections.list.ListServiceRestate;
import dev.restate.e2e.services.receiver.ReceiverGrpc;
import dev.restate.e2e.services.receiver.ReceiverProto.GetValueRequest;
import dev.restate.e2e.services.receiver.ReceiverProto.PingRequest;
import dev.restate.e2e.services.receiver.ReceiverProto.SetValueRequest;
import dev.restate.e2e.services.receiver.ReceiverRestate;
import dev.restate.sdk.Awaitable;
import dev.restate.sdk.RestateContext;
import dev.restate.sdk.common.CoreSerdes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoordinatorService extends CoordinatorRestate.CoordinatorRestateImplBase {
  private static final Logger LOG = LogManager.getLogger(CoordinatorService.class);

  @Override
  public void sleep(RestateContext context, Duration request) {
    manyTimers(context, ManyTimersRequest.newBuilder().addTimer(request).build());
  }

  @Override
  public void manyTimers(RestateContext context, ManyTimersRequest request) {
    LOG.info("many timers {}", request.getTimerList());

    awaitableAll(
        request.getTimerList().stream()
            .map(d -> context.timer(java.time.Duration.ofMillis(d.getMillis())))
            .collect(Collectors.toList()));
  }

  @Override
  public ProxyResponse proxy(RestateContext context) {
    String key = context.sideEffect(CoreSerdes.STRING_UTF8, () -> UUID.randomUUID().toString());

    var pong =
        ReceiverRestate.newClient().ping(PingRequest.newBuilder().setKey(key).build()).await();

    return ProxyResponse.newBuilder().setMessage(pong.getMessage()).build();
  }

  @Override
  public ComplexResponse complex(RestateContext context, ComplexRequest request) {
    LOG.info(
        "Starting complex coordination by sleeping for {} ms",
        request.getSleepDuration().getMillis());

    context.sleep(java.time.Duration.ofMillis(request.getSleepDuration().getMillis()));

    var receiverUUID =
        context.sideEffect(CoreSerdes.STRING_UTF8, () -> UUID.randomUUID().toString());
    var receiverClient = ReceiverRestate.newClient();

    LOG.info("Send fire and forget call to {}", ReceiverGrpc.getServiceDescriptor().getName());
    // services should be invoked in the same order they were called. This means that
    // background calls as well as request-response calls have an absolute ordering that is defined
    // by their call order. In this concrete case, setValue is guaranteed to be executed before
    // getValue.
    receiverClient
        .oneWay()
        .setValue(
            SetValueRequest.newBuilder()
                .setKey(receiverUUID)
                .setValue(request.getRequestValue())
                .build());

    LOG.info("Get current value from {}", ReceiverGrpc.getServiceDescriptor().getName());
    var response =
        receiverClient.getValue(GetValueRequest.newBuilder().setKey(receiverUUID).build()).await();

    LOG.info("Finish complex coordination with response value '{}'", response.getValue());
    return ComplexResponse.newBuilder().setResponseValue(response.getValue()).build();
  }

  @Override
  public TimeoutResponse timeout(RestateContext context, Duration request) {
    var timeoutOccurred = false;

    var awakeable = context.awakeable(CoreSerdes.VOID);
    try {
      awakeable.await(java.time.Duration.ofMillis(request.getMillis()));
    } catch (TimeoutException te) {
      timeoutOccurred = true;
    }

    return TimeoutResponse.newBuilder().setTimeoutOccurred(timeoutOccurred).build();
  }

  @Override
  public void invokeSequentially(RestateContext context, InvokeSequentiallyRequest request) {
    List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

    var listClient = ListServiceRestate.newClient();
    for (int i = 0; i < request.getExecuteAsBackgroundCallCount(); i++) {
      var appendRequest =
          AppendRequest.newBuilder()
              .setListName(request.getListName())
              .setValue(String.valueOf(i))
              .build();
      if (request.getExecuteAsBackgroundCall(i)) {
        listClient.oneWay().append(appendRequest);
      } else {
        collectedAwaitables.add(listClient.append(appendRequest));
      }
    }

    awaitableAll(collectedAwaitables);
  }

  private static void awaitableAll(List<Awaitable<?>> awaitables) {
    if (awaitables.size() == 1) {
      awaitables.get(0).await();
    } else if (awaitables.size() == 2) {
      Awaitable.all(awaitables.get(0), awaitables.get(1)).await();
    } else if (awaitables.size() >= 2) {
      Awaitable.all(
              awaitables.get(0),
              awaitables.get(1),
              awaitables.subList(2, awaitables.size()).toArray(Awaitable[]::new))
          .await();
    }
  }
}

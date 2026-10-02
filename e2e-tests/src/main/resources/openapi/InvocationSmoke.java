// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.ingress.api.InvocationsOutputAndStatusApi;
import dev.restate.ingress.client.ApiClient;
import dev.restate.ingress.client.ApiResponse;
import dev.restate.ingress.model.ByInvocationId;
import dev.restate.ingress.model.RestateInvocationStatus;
import dev.restate.ingress.model.RestateInvocationTarget;
import dev.restate.ingress.model.RestateSendResponse;
import java.net.URI;
import java.time.Duration;

/** Compiled independently against each service's generated invocation API. */
public class InvocationSmoke {
  public static ApiClient client(URI ingress) {
    return new ApiClient()
        .setHost(ingress.getHost())
        .setPort(ingress.getPort())
        .setReadTimeout(Duration.ofSeconds(30));
  }

  public static void verify(ApiClient client, ApiResponse<RestateSendResponse> sent)
      throws Exception {
    assertThat(sent.getStatusCode()).isEqualTo(202);
    assertThat(sent.getData().getStatus()).isEqualTo(RestateSendResponse.StatusEnum.ACCEPTED);
    var id = sent.getData().getInvocationId();
    assertThat(id).isNotBlank();
    var target =
        new RestateInvocationTarget(
            new ByInvocationId().target(ByInvocationId.TargetEnum.INVOCATION).invocationId(id));
    var invocations = new InvocationsOutputAndStatusApi(client);

    // Attach waits for completion before output/status. These generic output operations have no
    // typed response schema; typed payload round trips are checked by the handler call operations.
    assertThat(invocations.attachInvocationWithHttpInfo(id).getStatusCode()).isEqualTo(200);
    assertThat(invocations.attachInvocationByTargetWithHttpInfo(target).getStatusCode())
        .isEqualTo(200);
    assertThat(invocations.getInvocationOutputWithHttpInfo(id).getStatusCode()).isEqualTo(200);
    assertThat(invocations.getInvocationOutputByTargetWithHttpInfo(target).getStatusCode())
        .isEqualTo(200);
    checkCompleted(invocations.getInvocationStatus(id));
    checkCompleted(invocations.getInvocationStatusByTarget(target));
  }

  private static void checkCompleted(RestateInvocationStatus status) {
    assertThat(status.getStage()).isEqualTo(RestateInvocationStatus.StageEnum.COMPLETED);
    assertThat(status.getError()).isNull();
  }
}

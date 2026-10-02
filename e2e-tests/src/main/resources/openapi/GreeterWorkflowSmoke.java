// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.ingress.api.DefaultApi;
import dev.restate.ingress.api.SendToHandlerApi;
import dev.restate.ingress.model.Details;
import dev.restate.ingress.model.RunRequest;
import java.net.URI;
import java.util.UUID;

public class GreeterWorkflowSmoke {
  public static void run(URI ingress) throws Exception {
    var client = InvocationSmoke.client(ingress);
    var calls = new DefaultApi(client);
    var sends = new SendToHandlerApi(client);
    var greeting = new RunRequest().name("Ada").details(new Details().language("en"));

    var response = calls.runWithHttpInfo(UUID.randomUUID().toString(), greeting);
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getData().getName()).isEqualTo("Ada");
    assertThat(response.getData().getDetails().getLanguage()).isEqualTo("en");

    var sentKey = UUID.randomUUID().toString();
    InvocationSmoke.verify(client, sends.runSendWithHttpInfo(sentKey, greeting, null));
    assertThat(calls.read(sentKey)).isEqualTo("ready");
  }
}

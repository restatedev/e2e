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
import dev.restate.ingress.model.GreetRequest;
import java.net.URI;
import java.util.UUID;

public class GreeterServiceSmoke {
  public static void run(URI ingress) throws Exception {
    var client = InvocationSmoke.client(ingress);
    var calls = new DefaultApi(client);
    var sends = new SendToHandlerApi(client);
    var greeting = new GreetRequest().name("Ada").details(new Details().language("en"));

    var response = calls.greetWithHttpInfo(greeting, null);
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getData().getName()).isEqualTo("Ada");
    assertThat(response.getData().getDetails().getLanguage()).isEqualTo("en");

    var idempotencyKey = UUID.randomUUID().toString();
    InvocationSmoke.verify(client, sends.greetSendWithHttpInfo(greeting, idempotencyKey, null));
    // Calling with the same idempotency key retrieves the sent invocation's typed result.
    assertThat(calls.greet(greeting, idempotencyKey)).isEqualTo(response.getData());
  }
}

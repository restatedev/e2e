// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

export class NumberSortHttpServerUtils {
  public static async sendSortNumbersRequest(
    replyId: string,
    numbers: number[]
  ): Promise<void> {
    const url = process.env.HTTP_SERVER_ADDRESS;
    if (url == undefined) {
      throw new Error("Supply the HTTP_SERVER_ADDRESS env variable");
    }
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "x-reply-id": replyId,
      },
      body: JSON.stringify(numbers),
    });

    if (!response.ok) {
      throw new Error("Response is not ok: " + response);
    }
  }
}

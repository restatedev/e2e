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
        "x-reply-id": Buffer.from(replyId).toString("base64"),
      },
      body: JSON.stringify(numbers),
    });

    if (!response.ok) {
      throw new Error("Response is not ok: " + response);
    }
  }
}

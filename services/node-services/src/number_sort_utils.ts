import * as http from 'http';
import * as querystring from 'querystring';

export class NumberSortHttpServerUtils {
  public static sendSortNumbersRequest(replyId: string, numbers: number[]): void {
    const request = this.prepareRequest(replyId, numbers);
    const req = http.request(request);
    req.on('error', (error) => {
      console.error(error);
    });
    req.end();
  }

  private static prepareRequest(replyId: string, numbers: number[]): http.RequestOptions {
    const requestBody = Buffer.from(JSON.stringify(numbers));
    const queryParams = querystring.stringify({ 'x-reply-id': Buffer.from(replyId).toString('base64') });

    return {
      method: 'PUT',
      host: process.env.HTTP_SERVER_ADDRESS,
      path: `/?${queryParams}`,
      headers: {
        'Content-Type': 'application/octet-stream',
        'Content-Length': requestBody.length.toString(),
      },
    };
  }
}

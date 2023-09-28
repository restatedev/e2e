import express, { Request, Response } from "express";

const app = express();
app.use(express.json());

app.get("/", async (req: Request, res: Response) => {
  // TODO have fun
  res.send('Hello World!');
});

export function startEmbeddedHandlerServer(port: number) {
  app.listen(port);
}

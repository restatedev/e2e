import express, { Request, Response } from "express";

// This contains the restate ingress address in the form http://hostname:port/
const restateUri = process.env.RESTATE_URI;

const app = express();
app.use(express.json());

app.get("/", async (req: Request, res: Response) => {
  // TODO have fun
  res.send("Hello Restate " + restateUri);
});

export function startEmbeddedHandlerServer(port: number) {
  app.listen(port);
}

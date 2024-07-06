package interpreter

import (
	"encoding/json"
	"net/http"
	"os"
)

// import { Test, TestConfiguration, TestStatus } from "./test_driver";

// let TEST: Test | undefined = undefined;

/**
 * Start a runner as a batch job
 */
func createJob() TestStatus {
	conf := readJsonEnv()
	test := NewTest(conf)
	return test.Go()
}

// /**
//  * Start a webserver
//  */
// export const createServer = (port: number) => {
//   http
//     .createServer(async function (req, res) {
//       const url = req.url ?? "/";
//       if (url === "/start" && req.method === "POST") {
//         const conf = await readJson<TestConfiguration>(req);
//         const status = TEST?.testStatus() ?? TestStatus.NOT_STARTED;

//         if (status !== TestStatus.NOT_STARTED) {
//           writeJsonResponse(400, { status }, res);
//           return;
//         }

//         TEST = new Test(conf);
//         TEST.go().catch((e) => console.log(e));

//         writeJsonResponse(200, { status: TEST.testStatus() }, res);
//       } else if (url === "/status") {
//         writeJsonResponse(
//           200,
//           { status: TEST?.testStatus() ?? TestStatus.NOT_STARTED },
//           res
//         );
//       } else if (url === "/sample") {
//         const conf = await readJson<TestConfiguration>(req);
//         const test = new Test(conf);
//         const generator = test.generate();
//         const itemSafe = generator.next();
//         if (!itemSafe.done) {
//           const { program } = itemSafe.value;
//           writeJsonResponse(200, program, res);
//         } else {
//           writeJsonResponse(
//             400,
//             { cause: "please specify at least one test" },
//             res
//           );
//         }
//       } else {
//         writeJsonResponse(404, {}, res);
//       }
//     })
//     .listen(port);
// };

func readJson[T any](req http.Request) (output T, err error) {
	err = json.NewDecoder(req.Body).Decode(&output)
	return
}

func writeJsonResponse(code int, body any, res http.ResponseWriter) {
	res.Header().Add("content-type", "application/json")
	res.WriteHeader(code)
	data, err := json.Marshal(body)
	if err != nil {
		panic(err)
	}
	res.Write(data)
}

func readJsonEnv() *TestConfiguration {
	conf := os.Getenv("INTERPRETER_DRIVER_CONF")
	if conf == "" {
		panic("Missing INTERPRETER_DRIVER_CONF env var")
	}
	config := &TestConfiguration{}
	json.Unmarshal([]byte(conf), conf)
	return config
}

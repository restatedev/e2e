package interpreter

import (
	"log"
	"os"
	"strconv"

	"github.com/restatedev/e2e/services/go-services/services"
	"github.com/restatedev/sdk-go/server"
)

/**
 * Hardcode for now, the Max number of InterpreterObject layers.
 * Each layer is represented by a VirtualObject that implements the ObjectInterpreter interface,
 * And named: `ObjectInterpreterL${layer}.
 *
 * i.e. (for 3 layers we get):
 *
 * ObjectInterpreterL0, ObjectInterpreterL1,ObjectInterpreterL2
 *
 * Each ObjectInterpreter is only allowed to preform blocking calls to the next layer,
 * to avoid deadlocks.
 *
 */

func init() {

	services.REGISTRY.AddRouter(serviceInterpreterHelper)
	services.REGISTRY.AddRouter(createInterpreterObject(0))
	services.REGISTRY.AddRouter(createInterpreterObject(1))
	services.REGISTRY.AddRouter(createInterpreterObject(2))

	services.REGISTRY.Add(services.Component{
		Fqdn: "InterpreterDriver",
		Binder: func(_ *server.Restate) {
			port := os.Getenv("INTERPRETER_DRIVER_PORT")
			if port == "" {
				port = "3000"
			}
			parsedPort, err := strconv.Atoi(port)
			if err != nil {
				log.Fatalf("Invalid INTERPRETER_DRIVER_PORT: %s", port)
			}
			createServer(parsedPort)
		},
	})

	services.REGISTRY.Add(services.Component{
		Fqdn: "InterpreterDriverJob",
		Binder: func(_ *server.Restate) {
			go func() {
				status, err := createJob()
				if err != nil {
					log.Fatalf("Job failure: %v", err)
				}

				log.Printf("Job success! %s\n", status)
			}()
		},
	})
}

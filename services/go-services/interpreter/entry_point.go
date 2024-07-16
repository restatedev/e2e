package interpreter

import (
	"github.com/restatedev/e2e/services/go-services/services"
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

func Register() {
	services.REGISTRY.AddRouter(ServiceInterpreterHelperRouter)
	services.REGISTRY.AddRouter(createInterpreterObject(0))
	services.REGISTRY.AddRouter(createInterpreterObject(1))
	services.REGISTRY.AddRouter(createInterpreterObject(2))
}

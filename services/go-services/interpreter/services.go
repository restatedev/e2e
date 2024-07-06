package interpreter

import (
	"fmt"
	"time"

	"github.com/restatedev/sdk-go"
)

/**
 * The following is an auxiliary service that is being called
 * by the interpreter objects
 */

const ServiceInterpreterHelper = "ServiceInterpreterHelper"

var ServiceInterpreterHelperRouter = restate.NewServiceRouter().
	Handler("ping", restate.NewServiceHandler(func(ctx restate.Context, _ restate.Void) (restate.Void, error) {
		return restate.Void{}, nil
	})).
	Handler("echo", restate.NewServiceHandler(func(ctx restate.Context, parameters string) (string, error) {
		return parameters, nil
	})).Handler("echoLater", restate.NewServiceHandler(func(ctx restate.Context, parameter struct {
	Sleep     time.Duration
	Parameter string
}) (string, error) {
	ctx.Sleep(time.Now().Add(parameter.Sleep * time.Second))
	return parameter.Parameter, nil
})).Handler("terminalFailure", restate.NewServiceHandler(func(ctx restate.Context, _ restate.Void) (string, error) {
	return "", restate.TerminalError(fmt.Errorf("bye"))
})).Handler("incrementIndirectly", restate.NewServiceHandler(func(ctx restate.Context, id InterpreterId) (restate.Void, error) {
	program := Program{
		Commands: []Command{IncrementStateCounter{}},
	}
	obj := interpreterObjectForLayer(id.layer)
	return restate.Void{}, ctx.Object(obj, id.key).Method("interpret").Send(program, 0)
}))

//     resolveAwakeable: async (ctx: restate.Context, id: string) => {
//       ctx.resolveAwakeable(id, "ok");
//     },

//     rejectAwakeable: async (ctx: restate.Context, id: string) => {
//       ctx.rejectAwakeable(id, "error");
//     },

//     incrementViaAwakeableDance: async (
//       ctx: restate.Context,
//       input: { interpreter: InterpreterId; txPromiseId: string }
//     ) => {
//       //
//       // 1. create an awakeable that we will be blocked on
//       //
//       const { id, promise } = ctx.awakeable<string>();
//       //
//       // 2. send our awakeable id to the interpreter via txPromise.
//       //
//       ctx.resolveAwakeable(input.txPromiseId, id);
//       //
//       // 3. wait for the interpreter resolve us
//       //
//       await promise;
//       //
//       // 4. to thank our interpret, let us ask it to inc its state.
//       //
//       const program: Program = {
//         commands: [
//           {
//             kind: CommandType.INCREMENT_STATE_COUNTER,
//           },
//         ],
//       };

//       const obj = interpreterObjectForLayer(input.interpreter.layer);

//       ctx.objectSendClient(obj, input.interpreter.key).interpret(program);
//     },
//   },
// });

// export type ServiceInterpreterHelper = typeof serviceInterpreterHelper;

package interpreter

import (
	"fmt"
	"log"
	"time"

	restate "github.com/restatedev/sdk-go"
)

/**
 * The following is an auxiliary service that is being called
 * by the interpreter objects
 */

const ServiceInterpreterHelper = "ServiceInterpreterHelper"

type EchoLaterRequest struct {
	Sleep     time.Duration `json:"sleep"`
	Parameter string        `json:"parameter"`
}

type IncrementViaAwakeableDanceRequest struct {
	Interpreter InterpreterId `json:"interpreter"`
	TxPromiseId string        `json:"txPromiseId"`
}

var ServiceInterpreterHelperRouter = restate.NewServiceRouter().
	Handler("ping",
		restate.NewServiceHandler(func(ctx restate.Context, _ restate.Void) (restate.Void, error) {
			return restate.Void{}, nil
		})).
	Handler("echo",
		restate.NewServiceHandler(func(ctx restate.Context, parameters string) (string, error) {
			return parameters, nil
		})).
	Handler("echoLater",
		restate.NewServiceHandler(func(ctx restate.Context, parameter EchoLaterRequest) (string, error) {
			ctx.Sleep(time.Now().Add(parameter.Sleep * time.Second))
			return parameter.Parameter, nil
		})).
	Handler("terminalFailure",
		restate.NewServiceHandler(func(ctx restate.Context, _ restate.Void) (string, error) {
			return "", restate.TerminalError(fmt.Errorf("bye"))
		})).
	Handler("incrementIndirectly",
		restate.NewServiceHandler(func(ctx restate.Context, id InterpreterId) (restate.Void, error) {
			program := Program{
				Commands: []Command{IncrementStateCounter{}},
			}
			obj := interpreterObjectForLayer(id.Layer)
			log.Printf("sending to interpret obj %s with key %s", obj, id.Key)
			return restate.Void{}, ctx.Object(obj, id.Key).Method("interpret").Send(program, 0)
		})).
	Handler("resolveAwakeable",
		restate.NewServiceHandler(func(ctx restate.Context, id string) (restate.Void, error) {
			return restate.Void{}, restate.ResolveAwakeableAs(ctx, id, "ok")
		})).
	Handler("rejectAwakeable",
		restate.NewServiceHandler(func(ctx restate.Context, id string) (restate.Void, error) {
			return restate.Void{}, ctx.RejectAwakeable(id, fmt.Errorf("error"))
		})).
	Handler("incrementViaAwakeableDance",
		restate.NewServiceHandler(func(ctx restate.Context, input IncrementViaAwakeableDanceRequest) (restate.Void, error) {
			//
			// 1. create an awakeable that we will be blocked on
			//
			awakeable, err := restate.AwakeableAs[string](ctx)
			if err != nil {
				return restate.Void{}, err
			}
			//
			// 2. send our awakeable id to the interpreter via txPromise.
			//
			if err := restate.ResolveAwakeableAs[string](ctx, input.TxPromiseId, awakeable.Id()); err != nil {
				return restate.Void{}, err
			}
			//
			// 3. wait for the interpreter resolve us
			//
			<-awakeable.Chan()
			//
			// 4. to thank our interpret, let us ask it to inc its state.
			//
			program := Program{
				Commands: []Command{
					IncrementStateCounter{},
				},
			}

			obj := interpreterObjectForLayer(input.Interpreter.Layer)

			return restate.Void{}, ctx.Object(obj, input.Interpreter.Key).Method("interpret").Send(program, 0)
		}))

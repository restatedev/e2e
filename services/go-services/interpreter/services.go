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

type EchoLaterRequest struct {
	Sleep     time.Duration `json:"sleep"`
	Parameter string        `json:"parameter"`
}

type IncrementViaAwakeableDanceRequest struct {
	Interpreter InterpreterId `json:"interpreter"`
	TxPromiseId string        `json:"txPromiseId"`
}

var ServiceInterpreterHelperRouter = restate.Service(ServiceInterpreterHelper{})

type ServiceInterpreterHelper struct {
}

func (s ServiceInterpreterHelper) Ping(ctx restate.Context, _ restate.Void) (restate.Void, error) {
	return restate.Void{}, nil
}

func (s ServiceInterpreterHelper) Echo(ctx restate.Context, parameters string) (string, error) {
	return parameters, nil
}

func (s ServiceInterpreterHelper) EchoLater(ctx restate.Context, parameter EchoLaterRequest) (string, error) {
	ctx.Sleep(parameter.Sleep)
	return parameter.Parameter, nil
}

func (s ServiceInterpreterHelper) TerminalFailure(ctx restate.Context, _ restate.Void) (string, error) {
	return "", restate.TerminalError(fmt.Errorf("bye"))
}

func (s ServiceInterpreterHelper) IncrementIndirectly(ctx restate.Context, id InterpreterId) (restate.Void, error) {
	program := Program{
		Commands: []Command{IncrementStateCounter{}},
	}
	obj := interpreterObjectForLayer(id.Layer)
	log.Printf("sending to interpret obj %s with key %s", obj, id.Key)
	return restate.Void{}, ctx.Object(obj, id.Key, "interpret").Send(program, 0)
}

func (s ServiceInterpreterHelper) ResolveAwakeable(ctx restate.Context, id string) (restate.Void, error) {
	return restate.Void{}, ctx.ResolveAwakeable(id, "ok")
}

func (s ServiceInterpreterHelper) RejectAwakeable(ctx restate.Context, id string) (restate.Void, error) {
	ctx.RejectAwakeable(id, fmt.Errorf("error"))
	return restate.Void{}, nil
}

func (s ServiceInterpreterHelper) IncrementViaAwakeableDance(ctx restate.Context, input IncrementViaAwakeableDanceRequest) (restate.Void, error) {
	//
	// 1. create an awakeable that we will be blocked on
	//
	awakeable := restate.AwakeableAs[string](ctx)

	//
	// 2. send our awakeable id to the interpreter via txPromise.
	//
	if err := ctx.ResolveAwakeable(input.TxPromiseId, awakeable.Id()); err != nil {
		return restate.Void{}, err
	}
	//
	// 3. wait for the interpreter resolve us
	//
	if _, err := awakeable.Result(); err != nil {
		return restate.Void{}, err
	}
	//
	// 4. to thank our interpret, let us ask it to inc its state.
	//
	program := Program{
		Commands: []Command{
			IncrementStateCounter{},
		},
	}

	obj := interpreterObjectForLayer(input.Interpreter.Layer)

	return restate.Void{}, ctx.Object(obj, input.Interpreter.Key, "interpret").Send(program, 0)
}

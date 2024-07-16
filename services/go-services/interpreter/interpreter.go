package interpreter

import (
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"time"

	restate "github.com/restatedev/sdk-go"
)

type InterpreterId struct {
	Layer int    `json:"layer"`
	Key   string `json:"key"`
}

func interpreterObjectForLayer(layer int) string {
	return fmt.Sprintf("ObjectInterpreterL%d", layer)
}

func createInterpreterObject(layer int) *restate.ObjectRouter {
	return restate.NewObjectRouter(interpreterObjectForLayer(layer)).
		Handler("counter", restate.NewObjectHandler(
			func(ctx restate.ObjectContext, _ restate.Void) (int, error) {
				res, err := restate.GetAs[int](ctx, STATE_COUNTER_NAME)
				if err != nil && err != restate.ErrKeyNotFound {
					return 0, err
				}
				return res, nil
			})).
		Handler("interpret", restate.NewObjectHandler(
			func(ctx restate.ObjectContext, program Program) (restate.Void, error) {
				return restate.Void{}, NewProgramInterpreter(ctx.Key(), layer).interpret(ctx, program)
			}))
}

type InterpreterObject interface {
	counter(ctx restate.ObjectContext) (int, error)
	interpret(ctx restate.ObjectContext, program Program) (restate.Void, error)
}

/**
 * Represents a promise to be awaited on.
 * we delay *any* chaining to the promise as close as possible
 * to the moment that promise is being awaited, because sometimes chaining
 * might actually create a side effect (for example trigger a suspension timer).
 *
 * There is also an expected value to verify that the result of the promise strictly matches to the expected value.
 */
type Await struct {
	expected            string
	expectedTerminalErr bool
	future              func() (string, error)
}

const Service = "ServiceInterpreterHelper"
const STATE_COUNTER_NAME = "counter"

type ProgramInterpreter struct {
	interpreterId InterpreterId
}

func NewProgramInterpreter(key string, layer int) *ProgramInterpreter {
	return &ProgramInterpreter{interpreterId: InterpreterId{Layer: layer, Key: key}}
}

func (p *ProgramInterpreter) interpret(ctx restate.ObjectContext, program Program) error {
	log.Printf("interpreting with id %+v\n", p.interpreterId)
	promises := map[int]Await{}
	commands := program.Commands
	for i := 0; i < len(commands); i++ {
		switch command := commands[i].(type) {
		case *SetState:
			log.Printf("setting state: %+v\n", command)
			if err := ctx.Set(fmt.Sprintf("key-%d", command.Key), fmt.Sprintf("value-%d", command.Key)); err != nil {
				return err
			}
		case *GetState:
			log.Printf("getting state: %+v\n", command)
			if _, err := restate.GetAs[string](ctx, fmt.Sprintf("key-%d", command.Key)); err != nil && err != restate.ErrKeyNotFound {
				return err
			}
		case *ClearState:
			log.Printf("clearing state: %+v\n", command)
			ctx.Clear(fmt.Sprintf("key-%d", command.Key))
		case *IncrementStateCounter:
			log.Printf("incrementing state: %+v\n", command)
			counter, err := restate.GetAs[int](ctx, STATE_COUNTER_NAME)
			if err != nil && err != restate.ErrKeyNotFound {
				return err
			}
			if err := ctx.Set(STATE_COUNTER_NAME, counter+1); err != nil {
				return err
			}
		case *Sleep:
			log.Printf("sleeping: %+v\n", command)
			ctx.Sleep(command.Duration)
		case *CallService:
			log.Printf("calling service: %+v\n", command)
			expected := fmt.Sprintf("hello-%d", i)
			fut, err := ctx.Service(Service, "Echo").RequestFuture(expected)
			if err != nil {
				return err
			}
			promises[i] = Await{
				future: func() (string, error) {
					var val string
					return val, fut.Response(&val)
				},
				expected: expected,
			}
		case *IncrementViaDelayedCall:
			log.Printf("incrementing via delayed call: %+v\n", command)
			if err := ctx.Service(Service, "IncrementIndirectly").Send(p.interpreterId, command.Duration); err != nil {
				return err
			}
		case *CallSlowService:
			log.Printf("calling slow service: %+v\n", command)
			expected := fmt.Sprintf("hello-%d", i)
			fut, err := ctx.Service(Service, "EchoLater").RequestFuture(EchoLaterRequest{
				Sleep:     command.Sleep,
				Parameter: expected,
			})
			if err != nil {
				return err
			}
			promises[i] = Await{
				future: func() (string, error) {
					var val string
					return val, fut.Response(&val)
				},
				expected: expected,
			}
		case *SideEffect:
			log.Printf("executing side effect: %+v\n", command)
			expected := fmt.Sprintf("hello-%d", i)
			result, err := restate.RunAs(ctx, func(_ restate.RunContext) (string, error) {
				return expected, nil
			})
			if err != nil {
				return err
			}
			if result != expected {
				return restate.TerminalError(fmt.Errorf("RPC failure %s != %s", result, expected))
			}
		case *SlowSideEffect:
			log.Printf("executing slow side effect: %+v\n", command)
			if _, err := restate.RunAs(ctx, func(_ restate.RunContext) ([]byte, error) {
				time.Sleep(1 * time.Millisecond)
				return nil, nil
			}, restate.WithBinary); err != nil {
				return err
			}
		case *RecoverTerminalCall:
			log.Printf("recovering terminal call: %+v\n", command)
			err := ctx.Service(Service, "TerminalFailure").Request(restate.Void{}, restate.Void{})
			if !restate.IsTerminalError(err) {
				return restate.TerminalError(fmt.Errorf("Test assertion failed, was expected to get a terminal error."))
			}
		case *RecoverTerminalCallMaybeUnAwaited:
			log.Printf("recovering terminal call (maybe unawaited): %+v\n", command)
			fut, err := ctx.Service(Service, "TerminalFailure").RequestFuture(restate.Void{})
			if err != nil {
				return err
			}
			promises[i] = Await{
				future:              func() (string, error) { return "", fut.Response(&restate.Void{}) },
				expectedTerminalErr: true,
			}
		case *ThrowingSideEffect:
			log.Printf("executing throwing side effect: %+v\n", command)
			if _, err := restate.RunAs(ctx, func(_ restate.RunContext) ([]byte, error) {
				if rand.Float64() < 0.5 {
					return nil, fmt.Errorf("undefined is not a number, but it still has feelings.")
				}
				return nil, nil
			}, restate.WithBinary); err != nil {
				return err
			}
		case *IncrementStateCounterIndirectly:
			log.Printf("incrementing state counter indirectly: %+v\n", command)
			if err := ctx.Service(Service, "IncrementIndirectly").Send(p.interpreterId, 0); err != nil {
				return err
			}
		case *AwaitPromise:
			log.Printf("awaiting promise: %+v\n", command)
			index := command.Index
			toAwait, ok := promises[index]
			if !ok {
				// Unexpected. This can be an interpreter bug, and can be a real issue.
				// Not very helpful I know :( but this is truly unexpected to have happen.
				return restate.TerminalError(fmt.Errorf("ObjectInterpreter: can not find a promise for the id %d.", index))
			}
			delete(promises, index)
			future, expected, expectedTerminalErr := toAwait.future, toAwait.expected, toAwait.expectedTerminalErr
			result, err := future()
			if result != expected {
				originalCommandWas := commands[index]
				return restate.TerminalError(fmt.Errorf("Awaited promise mismatch. got %s  expected %s ; command %v", result, expected, originalCommandWas))
			}
			if expectedTerminalErr && !restate.IsTerminalError(err) {
				originalCommandWas := commands[index]
				return restate.TerminalError(fmt.Errorf("Awaited promise mismatch. got error %v  expected terminal ; command %v", err, originalCommandWas))
			}
			if !expectedTerminalErr && err != nil {
				return err
			}
		case *ResolveAwakeable:
			log.Printf("resolving awakeable: %+v\n", command)
			awakeable := restate.AwakeableAs[string](ctx)
			promises[i] = Await{expected: "ok", future: awakeable.Result}
			if err := ctx.Service(Service, "ResolveAwakeable").Send(awakeable.Id(), 0); err != nil {
				return err
			}
		case *RejectAwakeable:
			log.Printf("rejecting awakeable: %+v\n", command)
			awakeable := restate.AwakeableAs[string](ctx)
			promises[i] = Await{expectedTerminalErr: true, future: awakeable.Result}
			if err := ctx.Service(Service, "RejectAwakeable").Send(awakeable.Id(), 0); err != nil {
				return err
			}
		case *IncrementStateCounterViaAwakeable:
			log.Printf("incrementing state counter via awakeable: %+v\n", command)
			// there is a complicated dance here.
			awakeable := restate.AwakeableAs[string](ctx)
			if err := ctx.Service(Service, "IncrementViaAwakeableDance").Send(IncrementViaAwakeableDanceRequest{
				Interpreter: p.interpreterId,
				TxPromiseId: awakeable.Id(),
			}, 0); err != nil {
				return err
			}
			// wait for the helper service to give us a promise to resolve.
			theirPromiseIdForUsToResolve, err := awakeable.Result()
			if err != nil {
				return err
			}
			// let us resolve it
			if err := ctx.ResolveAwakeable(theirPromiseIdForUsToResolve, "ok"); err != nil {
				return err
			}
		case *CallObject:
			log.Printf("calling next layer object: %+v\n", command)
			nextLayer := p.interpreterId.Layer + 1
			key := fmt.Sprintf("%d", command.Key)
			def := interpreterObjectForLayer(nextLayer)

			program := command.Program
			b, _ := json.Marshal(program)
			log.Printf("outbound program: %s\n", string(b))
			fut, err := ctx.Object(def, key, "interpret").RequestFuture(program)
			if err != nil {
				return err
			}
			promises[i] = Await{
				future: func() (string, error) {
					return "", fut.Response(&restate.Void{})
				},
				expected: "",
			}
		}
	}
	return nil
}

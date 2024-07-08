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

func createInterpreterObject(layer int) (string, *restate.ObjectRouter) {
	return interpreterObjectForLayer(layer), restate.NewObjectRouter().Handler("counter", restate.NewObjectHandler(func(ctx restate.ObjectContext, _ restate.Void) (int, error) {
		res, err := restate.GetAs[int](ctx, STATE_COUNTER_NAME)
		if err != nil && err != restate.ErrKeyNotFound {
			return 0, err
		}
		return res, nil
	})).Handler("interpret", restate.NewObjectHandler(func(ctx restate.ObjectContext, program Program) (restate.Void, error) {
		return restate.Void{}, NewProgramInterpreter(layer, ctx).interpret(program)
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
	future              func() <-chan restate.Result[string]
}

const Service = "ServiceInterpreterHelper"
const STATE_COUNTER_NAME = "counter"

type ProgramInterpreter struct {
	ctx           restate.ObjectContext
	interpreterId InterpreterId
}

func NewProgramInterpreter(layer int, ctx restate.ObjectContext) *ProgramInterpreter {
	return &ProgramInterpreter{ctx: ctx, interpreterId: InterpreterId{Layer: layer, Key: ctx.Key()}}
}

func (p *ProgramInterpreter) interpret(program Program) error {
	log.Printf("interpreting with id %+v\n", p.interpreterId)
	ctx := p.ctx
	promises := map[int]Await{}
	commands := program.Commands
	for i := 0; i < len(commands); i++ {
		switch command := commands[i].(type) {
		case *SetState:
			log.Printf("setting state: %+v\n", command)
			if err := restate.SetAs(ctx, fmt.Sprintf("key-%d", command.Key), fmt.Sprintf("value-%d", command.Key)); err != nil {
				return err
			}
		case *GetState:
			log.Printf("getting state: %+v\n", command)
			if _, err := restate.GetAs[string](ctx, fmt.Sprintf("key-%d", command.Key)); err != nil && err != restate.ErrKeyNotFound {
				return err
			}
		case *ClearState:
			log.Printf("clearing state: %+v\n", command)
			if err := ctx.Clear(fmt.Sprintf("key-%d", command.Key)); err != nil {
				return err
			}
		case *IncrementStateCounter:
			log.Printf("incrementing state: %+v\n", command)
			counter, err := restate.GetAs[int](ctx, STATE_COUNTER_NAME)
			if err != nil && err != restate.ErrKeyNotFound {
				return err
			}
			if err := restate.SetAs(ctx, STATE_COUNTER_NAME, counter+1); err != nil {
				return err
			}
		case *Sleep:
			log.Printf("sleeping: %+v\n", command)
			if err := ctx.Sleep(time.Now().Add(command.Duration)); err != nil {
				return err
			}
		case *CallService:
			log.Printf("calling service: %+v\n", command)
			expected := fmt.Sprintf("hello-%d", i)
			future := make(chan restate.Result[string], 1)
			go func() {
				var val string
				err := ctx.Service(Service).Method("echo").Do(expected, &val)
				future <- restate.Result[string]{Value: val, Err: err}
			}()
			promises[i] = Await{
				future:   func() <-chan restate.Result[string] { return future },
				expected: expected,
			}
		case *IncrementViaDelayedCall:
			log.Printf("incrementing via delayed call: %+v\n", command)
			ctx.Service(Service).Method("incrementIndirectly").Send(p.interpreterId, command.Duration)
		case *CallSlowService:
			log.Printf("calling slow service: %+v\n", command)
			expected := fmt.Sprintf("hello-%d", i)
			future := make(chan restate.Result[string], 1)
			go func() {
				var val string
				err := ctx.Service(Service).Method("echoLater").Do(EchoLaterRequest{
					Sleep:     command.Sleep,
					Parameter: expected,
				}, &val)
				future <- restate.Result[string]{Value: val, Err: err}
			}()
			promises[i] = Await{
				future:   func() <-chan restate.Result[string] { return future },
				expected: expected,
			}
		case *SideEffect:
			log.Printf("executing side effect: %+v\n", command)
			expected := fmt.Sprintf("hello-%d", i)
			result, err := restate.SideEffectAs(ctx, func() (string, error) {
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
			if _, err := ctx.SideEffect(func() ([]byte, error) {
				time.Sleep(1 * time.Millisecond)
				return nil, nil
			}); err != nil {
				return err
			}
		case *RecoverTerminalCall:
			log.Printf("recovering terminal call: %+v\n", command)
			err := ctx.Service(Service).Method("terminalFailure").Do(restate.Void{}, &restate.Void{})
			if !restate.IsTerminalError(err) {
				return restate.TerminalError(fmt.Errorf("Test assertion failed, was expected to get a terminal error."))
			}
		case *RecoverTerminalCallMaybeUnAwaited:
			log.Printf("recovering terminal call (maybe unawaited): %+v\n", command)
			future := make(chan restate.Result[string], 1)
			go func() {
				err := ctx.Service(Service).Method("terminalFailure").Do(restate.Void{}, &restate.Void{})
				future <- restate.Result[string]{Err: err}
			}()
			promises[i] = Await{
				future:              func() <-chan restate.Result[string] { return future },
				expectedTerminalErr: true,
			}
		case *ThrowingSideEffect:
			log.Printf("executing throwing side effect: %+v\n", command)
			if _, err := ctx.SideEffect(func() ([]byte, error) {
				if rand.Float64() < 0.5 {
					return nil, fmt.Errorf("undefined is not a number, but it still has feelings.")
				}
				return nil, nil
			}); err != nil {
				return err
			}
		case *IncrementStateCounterIndirectly:
			log.Printf("incrementing state counter indirectly: %+v\n", command)
			if err := ctx.Service(Service).Method("incrementIndirectly").Send(p.interpreterId, 0); err != nil {
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
			result := <-future()
			if result.Value != expected {
				originalCommandWas := commands[index]
				return restate.TerminalError(fmt.Errorf("Awaited promise mismatch. got %s  expected %s ; command %v", result.Value, expected, originalCommandWas))
			}
			if expectedTerminalErr && !restate.IsTerminalError(result.Err) {
				originalCommandWas := commands[index]
				return restate.TerminalError(fmt.Errorf("Awaited promise mismatch. got error %v  expected terminal ; command %v", result.Err, originalCommandWas))
			}
			if !expectedTerminalErr && result.Err != nil {
				return result.Err
			}
		case *ResolveAwakeable:
			log.Printf("resolving awakeable: %+v\n", command)
			awakeable, err := restate.AwakeableAs[string](ctx)
			if err != nil {
				return err
			}
			promises[i] = Await{expected: "ok", future: awakeable.Chan}
			if err := ctx.Service(Service).Method("resolveAwakeable").Send(awakeable.Id(), 0); err != nil {
				return err
			}
		case *RejectAwakeable:
			log.Printf("rejecting awakeable: %+v\n", command)
			awakeable, err := restate.AwakeableAs[string](ctx)
			if err != nil {
				return err
			}
			promises[i] = Await{expectedTerminalErr: true, future: awakeable.Chan}
			if err := ctx.Service(Service).Method("rejectAwakeable").Send(awakeable.Id(), 0); err != nil {
				return err
			}
		case *IncrementStateCounterViaAwakeable:
			log.Printf("incrementing state counter via awakeable: %+v\n", command)
			// there is a complicated dance here.
			awakeable, err := restate.AwakeableAs[string](ctx)
			if err != nil {
				return err
			}
			if err := ctx.Service(Service).Method("incrementViaAwakeableDance").Send(IncrementViaAwakeableDanceRequest{
				Interpreter: p.interpreterId,
				TxPromiseId: awakeable.Id(),
			}, 0); err != nil {
				return err
			}
			// wait for the helper service to give us a promise to resolve.
			theirPromiseIdForUsToResolve := <-awakeable.Chan()
			if theirPromiseIdForUsToResolve.Err != nil {
				return theirPromiseIdForUsToResolve.Err
			}
			// let us resolve it
			if err := restate.ResolveAwakeableAs(ctx, theirPromiseIdForUsToResolve.Value, "ok"); err != nil {
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
			// safety: we must at least add a catch handler otherwise if the call results with a terminal exception propagated
			// and Node will cause this process to exit.
			future := make(chan restate.Result[string], 1)
			go func() {
				err := ctx.Object(def, key).Method("interpret").Do(program, &restate.Void{})
				future <- restate.Result[string]{Err: err}
			}()
			promises[i] = Await{
				future:   func() <-chan restate.Result[string] { return future },
				expected: "",
			}
		}
	}
	return nil
}

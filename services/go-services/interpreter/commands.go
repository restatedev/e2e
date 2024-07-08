package interpreter

import (
	"encoding/json"
	"fmt"
	"log"
	"time"
)

// Command AST

type CommandType int

const (
	SET_STATE CommandType = iota + 1
	GET_STATE
	CLEAR_STATE
	INCREMENT_STATE_COUNTER
	INCREMENT_STATE_COUNTER_INDIRECTLY
	SLEEP
	CALL_SERVICE
	CALL_SLOW_SERVICE
	INCREMENT_VIA_DELAYED_CALL
	SIDE_EFFECT
	THROWING_SIDE_EFFECT
	SLOW_SIDE_EFFECT
	RECOVER_TERMINAL_CALL
	RECOVER_TERMINAL_MAYBE_UN_AWAITED
	AWAIT_PROMISE
	RESOLVE_AWAKEABLE
	REJECT_AWAKEABLE
	INCREMENT_STATE_COUNTER_VIA_AWAKEABLE
	CALL_NEXT_LAYER_OBJECT
)

func (t CommandType) GetCommand() Command {
	switch t {
	case SET_STATE:
		return &SetState{}
	case GET_STATE:
		return &GetState{}
	case CLEAR_STATE:
		return &SetState{}
	case INCREMENT_STATE_COUNTER:
		return &IncrementStateCounter{}
	case INCREMENT_STATE_COUNTER_INDIRECTLY:
		return &IncrementStateCounterIndirectly{}
	case SLEEP:
		return &Sleep{}
	case CALL_SERVICE:
		return &CallService{}
	case CALL_SLOW_SERVICE:
		return &CallSlowService{}
	case INCREMENT_VIA_DELAYED_CALL:
		return &IncrementViaDelayedCall{}
	case SIDE_EFFECT:
		return &SideEffect{}
	case THROWING_SIDE_EFFECT:
		return &ThrowingSideEffect{}
	case SLOW_SIDE_EFFECT:
		return &SlowSideEffect{}
	case RECOVER_TERMINAL_CALL:
		return &RecoverTerminalCall{}
	case RECOVER_TERMINAL_MAYBE_UN_AWAITED:
		return &RecoverTerminalCallMaybeUnAwaited{}
	case AWAIT_PROMISE:
		return &AwaitPromise{}
	case RESOLVE_AWAKEABLE:
		return &ResolveAwakeable{}
	case REJECT_AWAKEABLE:
		return &RejectAwakeable{}
	case INCREMENT_STATE_COUNTER_VIA_AWAKEABLE:
		return &IncrementStateCounterViaAwakeable{}
	case CALL_NEXT_LAYER_OBJECT:
		return &CallObject{}
	}
	log.Fatalf("unexpected command type: %d", t)
	return nil
}

type Command interface {
	GetKind() CommandType
}

type Program struct {
	Commands []Command
}

type wireProgram struct {
	Commands []map[string]any `json:"commands"`
}

func (p Program) MarshalJSON() ([]byte, error) {
	program := wireProgram{Commands: make([]map[string]any, len(p.Commands))}
	for i := range p.Commands {
		data, err := json.Marshal(p.Commands[i])
		if err != nil {
			return nil, err
		}
		if err := json.Unmarshal(data, &program.Commands[i]); err != nil {
			return nil, err
		}
		program.Commands[i]["kind"] = p.Commands[i].GetKind()
	}
	return json.Marshal(program)
}

func (p *Program) UnmarshalJSON(data []byte) error {
	input := wireProgram{}
	if err := json.Unmarshal(data, &input); err != nil {
		return err
	}
	p.Commands = make([]Command, len(input.Commands))
	for i := range input.Commands {
		kind, ok := input.Commands[i]["kind"].(float64)
		if !ok {
			return fmt.Errorf("found command in program with no kind: %v", input.Commands[i])
		}
		delete(input.Commands[i], "kind")
		p.Commands[i] = CommandType(kind).GetCommand()
		data, err := json.Marshal(input.Commands[i])
		if err != nil {
			return err
		}
		if err := json.Unmarshal(data, p.Commands[i]); err != nil {
			return fmt.Errorf("failed to unmarshal '%s' into kind %d: %v", string(data), CommandType(kind), err)
		}
	}
	return nil
}

// No parameters
type IncrementStateCounter struct{}

func (c IncrementStateCounter) GetKind() CommandType { return INCREMENT_STATE_COUNTER }

type RecoverTerminalCall struct{}

func (c RecoverTerminalCall) GetKind() CommandType { return RECOVER_TERMINAL_CALL }

type RecoverTerminalCallMaybeUnAwaited struct{}

func (c RecoverTerminalCallMaybeUnAwaited) GetKind() CommandType {
	return RECOVER_TERMINAL_MAYBE_UN_AWAITED
}

type ThrowingSideEffect struct{}

func (c ThrowingSideEffect) GetKind() CommandType { return THROWING_SIDE_EFFECT }

type SlowSideEffect struct{}

func (c SlowSideEffect) GetKind() CommandType { return SLOW_SIDE_EFFECT }

type IncrementStateCounterIndirectly struct{}

func (c IncrementStateCounterIndirectly) GetKind() CommandType {
	return INCREMENT_STATE_COUNTER_INDIRECTLY
}

type ResolveAwakeable struct{}

func (c ResolveAwakeable) GetKind() CommandType { return RESOLVE_AWAKEABLE }

type RejectAwakeable struct{}

func (c RejectAwakeable) GetKind() CommandType { return REJECT_AWAKEABLE }

type IncrementStateCounterViaAwakeable struct{}

func (c IncrementStateCounterViaAwakeable) GetKind() CommandType {
	return INCREMENT_STATE_COUNTER_VIA_AWAKEABLE
}

type CallService struct{}

func (c CallService) GetKind() CommandType { return CALL_SERVICE }

type SideEffect struct{}

func (c SideEffect) GetKind() CommandType { return SIDE_EFFECT }

// State
type GetState struct {
	Key int `json:"key"`
}

func (c GetState) GetKind() CommandType { return GET_STATE }

type ClearState struct {
	Key int `json:"key"`
}

func (c ClearState) GetKind() CommandType { return CLEAR_STATE }

type SetState struct {
	Key int `json:"key"`
}

func (c SetState) GetKind() CommandType { return SET_STATE }

// Special
type Sleep struct {
	Duration time.Duration `json:"duration"`
}

func (c Sleep) GetKind() CommandType { return SLEEP }

type IncrementViaDelayedCall struct {
	Duration time.Duration `json:"duration"`
}

func (c IncrementViaDelayedCall) GetKind() CommandType { return INCREMENT_VIA_DELAYED_CALL }

type AwaitPromise struct {
	Index int `json:"index"`
}

func (c AwaitPromise) GetKind() CommandType { return AWAIT_PROMISE }

type CallSlowService struct {
	Sleep time.Duration `json:"sleep"`
}

func (c CallSlowService) GetKind() CommandType { return CALL_SLOW_SERVICE }

type CallObject struct {
	Key     int     `json:"key"`
	Program Program `json:"program"`
}

func (c CallObject) GetKind() CommandType { return CALL_NEXT_LAYER_OBJECT }

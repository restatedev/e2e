package interpreter

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

type Command interface {
	GetKind() CommandType
}

type Program struct {
	Commands []Command
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
	Key int
}

func (c GetState) GetKind() CommandType { return GET_STATE }

type ClearState struct {
	Key int
}

func (c ClearState) GetKind() CommandType { return CLEAR_STATE }

type SetState struct {
	Key int
}

func (c SetState) GetKind() CommandType { return SET_STATE }

// Special
type Sleep struct {
	Duration int
}

func (c Sleep) GetKind() CommandType { return SLEEP }

type IncrementViaDelayedCall struct {
	Duration int
}

func (c IncrementViaDelayedCall) GetKind() CommandType { return INCREMENT_VIA_DELAYED_CALL }

type AwaitPromise struct {
	Index int
}

func (c AwaitPromise) GetKind() CommandType { return AWAIT_PROMISE }

type CallSlowService struct {
	Sleep int
}

func (c CallSlowService) GetKind() CommandType { return CALL_SLOW_SERVICE }

type CallObject struct {
	Key     int
	Program Program
}

func (c CallObject) GetKind() CommandType { return CALL_NEXT_LAYER_OBJECT }

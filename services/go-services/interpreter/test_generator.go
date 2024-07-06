package interpreter

import (
	"math"
)

/**
 * The following module is responsible for generating instances of a {@link Program}.
 * According to a predefined distributed.
 * There is nothing here that defines correctness, it is just about
 */

/**
 * L0 distribution represents the commands with their 'rank' (~ likelihood to appear)
 * The exact statistical distribution is defined in the distribution function below.
 */
var L0 = distribution([][]CommandType{
	{
		GET_STATE,
		SET_STATE,
		CLEAR_STATE,
		SIDE_EFFECT,
		INCREMENT_STATE_COUNTER,
	},
	{
		INCREMENT_STATE_COUNTER_INDIRECTLY,
		CALL_SERVICE,
		CALL_NEXT_LAYER_OBJECT,
	},
	{
		INCREMENT_STATE_COUNTER_VIA_AWAKEABLE,
		RESOLVE_AWAKEABLE,
		REJECT_AWAKEABLE,
	},
	{THROWING_SIDE_EFFECT, RECOVER_TERMINAL_CALL},
	{
		CALL_SLOW_SERVICE,
		SLOW_SIDE_EFFECT,
		SLEEP,
		INCREMENT_VIA_DELAYED_CALL,
		RECOVER_TERMINAL_MAYBE_UN_AWAITED,
	},
})

/**
 * L1 distribution represents the commands with their 'rank' (~ likelihood to appear)
 */
var L1 = distribution([][]CommandType{
	{
		GET_STATE,
		SET_STATE,
		SIDE_EFFECT,
		INCREMENT_STATE_COUNTER,
	},
	{INCREMENT_STATE_COUNTER_INDIRECTLY, CALL_SERVICE},
	{
		CALL_NEXT_LAYER_OBJECT,
		INCREMENT_STATE_COUNTER_VIA_AWAKEABLE,
		RESOLVE_AWAKEABLE,
		REJECT_AWAKEABLE,
	},
	{THROWING_SIDE_EFFECT, RECOVER_TERMINAL_CALL},
	{
		CALL_SLOW_SERVICE,
		SLOW_SIDE_EFFECT,
		SLEEP,
		INCREMENT_VIA_DELAYED_CALL,
		RECOVER_TERMINAL_MAYBE_UN_AWAITED,
	},
})

/**
 * L2 distribution represents the commands with their 'rank' (~ likelihood to appear)
 */
var L2 = distribution([][]CommandType{
	{
		GET_STATE,
		SET_STATE,
		CLEAR_STATE,
		SIDE_EFFECT,
		INCREMENT_STATE_COUNTER,
	},
	{INCREMENT_STATE_COUNTER_INDIRECTLY, CALL_SERVICE},
	{THROWING_SIDE_EFFECT, RECOVER_TERMINAL_CALL},
	{SLEEP, INCREMENT_STATE_COUNTER_VIA_AWAKEABLE},
	{
		CALL_SLOW_SERVICE,
		SLOW_SIDE_EFFECT,
		INCREMENT_VIA_DELAYED_CALL,
		RECOVER_TERMINAL_MAYBE_UN_AWAITED,
	},
})

const MAX_NUMBER_OF_LEVELS = 3

var DISTRIBUTION_BY_LEVEL = []*WeightedRandom[CommandType]{L0, L1, L2}

type ProgramGenerator struct {
	rand                *Random
	interpreterCount    int
	maximumCommandCount int
}

func (p *ProgramGenerator) random(low int, high int) int {
	return int(math.Floor(p.rand.Random() * float64(high-low)))
}

func (p *ProgramGenerator) generateCommand(commandType CommandType, currentLevel int) Command {
	if commandType == SET_STATE {
		return SetState{
			Key: p.random(0, 6),
		}
	} else if commandType == GET_STATE {
		return GetState{
			Key: p.random(0, 6),
		}
	} else if commandType == CLEAR_STATE {
		return ClearState{
			Key: p.random(0, 6),
		}
	} else if commandType == INCREMENT_STATE_COUNTER {
		return IncrementStateCounter{}
	} else if commandType == INCREMENT_STATE_COUNTER_INDIRECTLY {
		return IncrementStateCounterIndirectly{}
	} else if commandType == SLEEP {
		return Sleep{
			Duration: p.random(1, 101),
		}
	} else if commandType == CALL_SERVICE {
		return CallService{}
	} else if commandType == CALL_SLOW_SERVICE {
		return CallSlowService{
			Sleep: p.random(1, 101),
		}
	} else if commandType == INCREMENT_VIA_DELAYED_CALL {
		return IncrementViaDelayedCall{
			Duration: p.random(1, 101),
		}
	} else if commandType == SIDE_EFFECT {
		return SideEffect{}
	} else if commandType == THROWING_SIDE_EFFECT {
		return ThrowingSideEffect{}
	} else if commandType == SLOW_SIDE_EFFECT {
		return SlowSideEffect{}
	} else if commandType == RECOVER_TERMINAL_CALL {
		return RecoverTerminalCall{}
	} else if commandType == RECOVER_TERMINAL_MAYBE_UN_AWAITED {
		return RecoverTerminalCallMaybeUnAwaited{}
	} else if commandType == RESOLVE_AWAKEABLE {
		return ResolveAwakeable{}
	} else if commandType == REJECT_AWAKEABLE {
		return RejectAwakeable{}
	} else if commandType == INCREMENT_STATE_COUNTER_VIA_AWAKEABLE {
		return IncrementStateCounterViaAwakeable{}
	} else if commandType == CALL_NEXT_LAYER_OBJECT && currentLevel < MAX_NUMBER_OF_LEVELS {
		key := p.random(0, p.interpreterCount)
		return CallObject{
			Key:     key,
			Program: p.generateProgram(currentLevel + 1),
		}
	}

	return nil
}

func (p *ProgramGenerator) generateProgram(currentLevel int) Program {
	numCommands := p.random(0, p.maximumCommandCount)
	var commands []Command
	var promises []int
	for i := 0; i < numCommands; i++ {
		commandType := DISTRIBUTION_BY_LEVEL[currentLevel].Next(&p.rand)
		command := p.generateCommand(commandType, currentLevel)
		if command == nil {
			continue
		}
		commands = append(commands, command)
		if canBeAwaited(commandType) {
			promises = append(promises, len(commands)-1)
		}
		if len(promises) > 0 && p.random(0, 100) < 80 {
			index := promises[0]
			promises = promises[1:]
			awaitCommand := AwaitPromise{
				Index: index,
			}
			commands = append(commands, awaitCommand)
		}
	}
	// last chance to await un-awaited commands
	for i := 0; i < len(promises); i++ {
		if len(promises) > 0 && p.random(0, 100) < 20 {
			// 20% skip
			continue
		}
		index := promises[i]
		awaitCommand := AwaitPromise{
			Index: index,
		}
		commands = append(commands, awaitCommand)
	}
	return Program{Commands: commands}
}

func distribution(ranks [][]CommandType) *WeightedRandom[CommandType] {
	commands := map[CommandType]int{
		SET_STATE:                             0,
		GET_STATE:                             0,
		CLEAR_STATE:                           0,
		INCREMENT_STATE_COUNTER:               0,
		INCREMENT_STATE_COUNTER_INDIRECTLY:    0,
		SLEEP:                                 0,
		CALL_SERVICE:                          0,
		CALL_SLOW_SERVICE:                     0,
		INCREMENT_VIA_DELAYED_CALL:            0,
		SIDE_EFFECT:                           0,
		THROWING_SIDE_EFFECT:                  0,
		SLOW_SIDE_EFFECT:                      0,
		RECOVER_TERMINAL_CALL:                 0,
		RECOVER_TERMINAL_MAYBE_UN_AWAITED:     0,
		AWAIT_PROMISE:                         0,
		RESOLVE_AWAKEABLE:                     0,
		REJECT_AWAKEABLE:                      0,
		INCREMENT_STATE_COUNTER_VIA_AWAKEABLE: 0,
		CALL_NEXT_LAYER_OBJECT:                0,
	}
	//
	// each group would have 2x smaller rank from the previous
	//
	rank := 1 << (len(ranks) + 1)
	for _, group := range ranks {
		for _, command := range group {
			commands[command] = rank
		}
		rank = rank >> 2
	}
	return NewWeightedRandomFromMap(commands)
}

func canBeAwaited(commandType CommandType) bool {
	return commandType == CALL_SERVICE ||
		commandType == CALL_SLOW_SERVICE ||
		commandType == RECOVER_TERMINAL_MAYBE_UN_AWAITED ||
		commandType == CALL_NEXT_LAYER_OBJECT
}

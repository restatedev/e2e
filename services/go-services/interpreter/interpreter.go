package interpreter

import "fmt"

type InterpreterId struct {
	layer int
	key   string
}

func interpreterObjectForLayer(layer int) string {
	return fmt.Sprintf("ObjectInterpreterL%d", layer)
}

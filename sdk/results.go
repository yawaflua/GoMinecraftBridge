package sdk

import (
	"encoding/json"
	"fmt"
)

// SystemCallError describes a failed named call returned by the host.
type SystemCallError struct {
	ID      string
	Name    string
	Message string
}

func (err *SystemCallError) Error() string {
	return fmt.Sprintf("system call %s (%s) failed: %s", err.Name, err.ID, err.Message)
}

// DecodeSystemCallResult validates a result and decodes its data into T.
func DecodeSystemCallResult[T any](result SystemCallResult) (T, error) {
	var value T
	if !result.Success {
		return value, &SystemCallError{ID: result.ID, Name: result.Name, Message: result.Error}
	}
	if len(result.Data) == 0 {
		return value, nil
	}
	if err := json.Unmarshal(result.Data, &value); err != nil {
		return value, fmt.Errorf("decode system call %s (%s): %w", result.Name, result.ID, err)
	}
	return value, nil
}

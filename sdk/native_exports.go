//go:build cgo && !wasm

package sdk

/*
#include <stdint.h>
#include <stdlib.h>
*/
import "C"

import (
	"fmt"
	"unsafe"
)

//export gmb_abi_version
func gmb_abi_version() C.int32_t {
	return C.int32_t(ABIVersion)
}

//export gmb_call
func gmb_call(
	operation C.int32_t,
	input *C.uint8_t,
	inputLength C.uint64_t,
	output **C.uint8_t,
	outputLength *C.uint64_t,
) (status C.int32_t) {
	*output = nil
	*outputLength = 0

	defer func() {
		if recovered := recover(); recovered != nil {
			fallback := []byte(fmt.Sprintf(`{"status":"panic","error":%q}`, fmt.Sprint(recovered)))
			writeNativeOutput(fallback, output, outputLength)
			status = 0
		}
	}()

	if inputLength > 64*1024*1024 {
		return 2
	}

	var payload []byte
	if inputLength > 0 {
		if input == nil {
			return 3
		}
		payload = C.GoBytes(unsafe.Pointer(input), C.int(inputLength))
	}

	writeNativeOutput(Dispatch(int(operation), payload), output, outputLength)
	return 0
}

//export gmb_free
func gmb_free(pointer unsafe.Pointer) {
	C.free(pointer)
}

func writeNativeOutput(payload []byte, output **C.uint8_t, outputLength *C.uint64_t) {
	if len(payload) == 0 {
		return
	}
	pointer := C.CBytes(payload)
	*output = (*C.uint8_t)(pointer)
	*outputLength = C.uint64_t(len(payload))
}

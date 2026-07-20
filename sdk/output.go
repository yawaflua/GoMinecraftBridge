package sdk

import (
	"bufio"
	"fmt"
	"io"
	"log"
	"os"
	"strings"
	"sync"
	"time"
)

var captured = struct {
	sync.Mutex
	logs []LogEntry
}{}

var outputCaptureOnce sync.Once

type streamCapture struct {
	writer *os.File
	marker string
	ack    chan struct{}
	mu     sync.Mutex
}

var outputStreams []*streamCapture

func enableOutputCapture() {
	outputCaptureOnce.Do(func() {
		outputStreams = append(outputStreams, captureStream("stdout", &os.Stdout))
		outputStreams = append(outputStreams, captureStream("stderr", &os.Stderr))
		log.SetOutput(os.Stderr)
	})
}

func captureStream(name string, destination **os.File) *streamCapture {
	reader, writer, err := os.Pipe()
	if err != nil {
		return nil
	}

	stream := &streamCapture{
		writer: writer,
		marker: fmt.Sprintf("\x00gmb-flush-%s-%d\x00", name, time.Now().UnixNano()),
		ack:    make(chan struct{}),
	}
	*destination = writer
	go func() {
		buffered := bufio.NewReader(reader)
		for {
			line, readErr := buffered.ReadString('\n')
			line = strings.TrimSuffix(strings.TrimSuffix(line, "\n"), "\r")
			if markerIndex := strings.Index(line, stream.marker); markerIndex >= 0 {
				captureLine(name, line[:markerIndex])
				captureLine(name, line[markerIndex+len(stream.marker):])
				stream.ack <- struct{}{}
			} else {
				captureLine(name, line)
			}
			if readErr != nil {
				if readErr != io.EOF {
					captureLine("stderr", "stdout capture failed: "+readErr.Error())
				}
				return
			}
		}
	}()
	return stream
}

func captureLine(stream, message string) {
	if message == "" {
		return
	}
	captured.Lock()
	captured.logs = append(captured.logs, LogEntry{
		Stream:             stream,
		Level:              streamLevel(stream),
		Message:            message,
		TimestampUnixMilli: time.Now().UnixMilli(),
	})
	captured.Unlock()
}

func drainCapturedLogs() []LogEntry {
	for _, stream := range outputStreams {
		if stream != nil {
			stream.flush()
		}
	}

	captured.Lock()
	defer captured.Unlock()
	logs := append([]LogEntry(nil), captured.logs...)
	captured.logs = captured.logs[:0]
	return logs
}

func (stream *streamCapture) flush() {
	stream.mu.Lock()
	defer stream.mu.Unlock()
	if _, err := stream.writer.WriteString(stream.marker + "\n"); err != nil {
		return
	}
	<-stream.ack
}

func streamLevel(stream string) string {
	if stream == "stderr" {
		return "error"
	}
	return "info"
}

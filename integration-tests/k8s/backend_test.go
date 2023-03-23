package k8s

import (
	"encoding/json"
	"fmt"
	ctracepb "go.opentelemetry.io/proto/otlp/collector/trace/v1"
	oltpCommon "go.opentelemetry.io/proto/otlp/common/v1"
	tracepb "go.opentelemetry.io/proto/otlp/trace/v1"
	"google.golang.org/protobuf/encoding/protojson"
	"net/http"
	"sigs.k8s.io/e2e-framework/pkg/env"
	"time"
)

const backendUrl = "http://localhost:32006"

func SetupOtelBackend(ns string) env.Func {
	return SetupManifest("config/otel-backend.yaml", ns, "")
}

func GetTraces() ([]ctracepb.ExportTraceServiceRequest, error) {
	resp, err := http.Get(fmt.Sprintf("%s/get-traces", backendUrl))
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to get traces: %d", resp.StatusCode)
	}

	defer resp.Body.Close()
	var raw []json.RawMessage
	err = json.NewDecoder(resp.Body).Decode(&raw)

	ret := make([]ctracepb.ExportTraceServiceRequest, len(raw))
	for i, r := range raw {
		err := protojson.Unmarshal(r, &ret[i])
		if err != nil {
			return nil, fmt.Errorf("failed to decode traces: %s", err)
		}
	}

	return ret, nil
}

func WaitForTraces(timeout time.Duration) ([]ctracepb.ExportTraceServiceRequest, error) {
	t := time.After(timeout)
	for {
		select {
		case <-t:
			return nil, fmt.Errorf("timed out waiting for traces")
		default:
			traces, err := GetTraces()
			if err != nil {
				return nil, err
			}
			if len(traces) > 0 {
				return traces, nil
			}
			time.Sleep(500 * time.Millisecond)
		}
	}
}

func Spans(traces []ctracepb.ExportTraceServiceRequest) []*tracepb.Span {
	var spans []*tracepb.Span
	for _, trace := range traces {
		for _, r := range trace.GetResourceSpans() {
			for _, ss := range r.GetScopeSpans() {
				for _, span := range ss.GetSpans() {
					spans = append(spans, span)
				}
			}
		}
	}

	return spans
}

func Attrs(traces []ctracepb.ExportTraceServiceRequest) []*oltpCommon.KeyValue {
	var attrs []*oltpCommon.KeyValue
	for _, span := range Spans(traces) {
		attrs = append(attrs, span.GetAttributes()...)
	}
	for _, trace := range traces {
		for _, r := range trace.GetResourceSpans() {
			attrs = append(attrs, r.GetResource().GetAttributes()...)
		}
	}
	return attrs
}

func CountSpansByName(traces []ctracepb.ExportTraceServiceRequest, name string) int {
	var count int
	for _, span := range Spans(traces) {
		if span.GetName() == name {
			count++
		}
	}
	return count
}

func CountSpansByAttributeValue(traces []ctracepb.ExportTraceServiceRequest, attributeName, attributeValue string) int {
	var count int
	for _, attr := range Attrs(traces) {
		if attr.GetKey() == attributeName && attr.GetValue().GetStringValue() == attributeValue {
			count++
		}
	}
	return count
}

func CountSpansByAttributeKey(traces []ctracepb.ExportTraceServiceRequest, attributeName string) int {
	var count int
	for _, attr := range Attrs(traces) {
		if attr.GetKey() == attributeName {
			count++
		}
	}
	return count
}

package k8s

import (
	"context"
	"fmt"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	ctracepb "go.opentelemetry.io/proto/otlp/collector/trace/v1"
	"io"
	"net/http"
	"sigs.k8s.io/e2e-framework/pkg/envconf"
	"sigs.k8s.io/e2e-framework/pkg/envfuncs"
	"sigs.k8s.io/e2e-framework/pkg/features"
	"testing"
	"time"
)

const tracesCtxKey = "traces"

func tracesFromContext(ctx context.Context) []ctracepb.ExportTraceServiceRequest {
	return ctx.Value(tracesCtxKey).([]ctracepb.ExportTraceServiceRequest)
}

func TestSpringboot(t *testing.T) {
	namespace := "springboot-agent"
	feature := features.New("Test the agent with Spring boot app").
		Setup(FeatureEnvFn(envfuncs.CreateNamespace(namespace))).
		Teardown(FeatureEnvFn(envfuncs.DeleteNamespace(namespace))).
		Setup(FeatureEnvFn(SetupOtelBackend(namespace))).
		Setup(FeatureEnvFn(SetupSpringboot(namespace, 8, map[string]string{}))).
		WithSetup("Call the service", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			require.NoError(t, greet(), "failed to GET /greeting")
			return ctx
		}).
		WithSetup("Wait for traces", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			traces, err := WaitForTraces(time.Second * 30)
			require.NoError(t, err, "failed to get traces")
			return context.WithValue(ctx, tracesCtxKey, traces)
		}).
		Assess("Check if the service was greeted", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			assert.Greater(t, CountSpansByName(tracesFromContext(ctx), "GET /greeting"), 0)
			return ctx
		}).
		Assess("Check if there's a greeting span", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			assert.Greater(t, CountSpansByName(tracesFromContext(ctx), "WebController.greeting"), 0)
			return ctx
		}).
		Assess("Check if there's a withSpan span", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			assert.Greater(t, CountSpansByName(tracesFromContext(ctx), "WebController.withSpan"), 0)
			return ctx
		}).
		Assess("Check if there's a lumigo.distro.version resource", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			assert.Greater(t, CountSpansByAttributeValue(tracesFromContext(ctx), "lumigo.distro.version", "dev-SNAPSHOT"), 0)
			return ctx
		}).
		Assess("Check if there's a k8s.pod.uid resource", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			assert.Greater(t, CountSpansByAttributeKey(tracesFromContext(ctx), "k8s.pod.uid"), 0)
			return ctx
		}).
		Feature()

	testEnv.Test(t, feature)
}

func greet() error {
	resp, err := http.Get(fmt.Sprintf("%s/greeting", springUrl))
	if err != nil {
		return fmt.Errorf("failed to get greeting: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	defer resp.Body.Close()
	content, err := io.ReadAll(resp.Body)
	if err != nil {
		fmt.Errorf("failed to read response body: %w", err)
	}

	if string(content) != "Hi!" {
		fmt.Errorf("unexpected response: %s", string(content))
	}

	return nil
}

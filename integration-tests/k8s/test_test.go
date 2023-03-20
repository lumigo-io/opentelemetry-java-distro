package k8s

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"sigs.k8s.io/e2e-framework/pkg/envconf"
	"sigs.k8s.io/e2e-framework/pkg/envfuncs"
	"sigs.k8s.io/e2e-framework/pkg/features"
	"testing"
	"time"
)

func TestSpringboot(t *testing.T) {
	namespace := "springboot-agent"
	feature := features.New("Test the agent with Spring boot app").
		Setup(FeatureEnvFn(envfuncs.CreateNamespace(namespace))).
		Teardown(FeatureEnvFn(envfuncs.DeleteNamespace(namespace))).
		Setup(FeatureEnvFn(SetupOtelBackend(namespace))).
		Setup(FeatureEnvFn(SetupSpringboot(namespace, 8, map[string]string{}))).
		Assess("Check if kubernetes resources are exists", func(ctx context.Context, t *testing.T, c *envconf.Config) context.Context {
			err := greet()
			if err != nil {
				t.Error(err)
				t.FailNow()
			}

			traces, err := WaitForTraces(time.Second * 30)
			if err != nil {
				t.Error(err)
				t.FailNow()
			}

			if CountSpansByName(traces, "GET /greeting") < 1 {
				t.Error("span `GET /greeting` not found")
				t.FailNow()
			}
			if CountSpansByName(traces, "WebController.greeting") < 1 {
				t.Error("span `` not found")
				t.FailNow()
			}
			if CountSpansByName(traces, "WebController.withSpan") < 1 {
				t.Error("span not found")
				t.FailNow()
			}

			if CountSpansByAttributeValue(traces, "lumigo.distro.version", "dev") < 1 {
				t.Error("lumigo.distro.version resource not found")
				t.FailNow()
			}

			if CountSpansByAttributeKey(traces, "k8s.pod.uid") < 1 {
				t.Error("k8s.pod.uid resource not found")
				t.FailNow()
			}

			return ctx
		}).Feature()

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

package k8s

import (
	"context"
	"fmt"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"os"
	"sigs.k8s.io/e2e-framework/klient/decoder"
	"sigs.k8s.io/e2e-framework/klient/k8s"
	"sigs.k8s.io/e2e-framework/klient/k8s/resources"
	"sigs.k8s.io/e2e-framework/klient/wait"
	"sigs.k8s.io/e2e-framework/klient/wait/conditions"
	"sigs.k8s.io/e2e-framework/pkg/env"
	"sigs.k8s.io/e2e-framework/pkg/envconf"
	"sigs.k8s.io/e2e-framework/pkg/features"
	"testing"
	"time"
)

const springUrl = "http://localhost:32010"

func FeatureEnvFn(fn env.Func) features.Func {
	return func(ctx context.Context, t *testing.T, cfg *envconf.Config) context.Context {
		ctx, err := fn(ctx, cfg)
		if err != nil {
			t.Error(err)
			t.FailNow()
		}
		return ctx
	}
}

func targetImage(jdk int) string {
	return fmt.Sprintf("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk%d-20211213.1570880324", jdk)
}
func SetupSpringboot(ns string, jdk int, extraEnvs map[string]string) env.Func {
	return SetupManifest("config/springboot.yaml", ns, "", decoder.MutateOption(func(obj k8s.Object) error {
		if obj.GetObjectKind().GroupVersionKind().Kind != "Deployment" {
			return nil
		}
		if obj.GetName() != "springboot" {
			return nil
		}

		dep := obj.(*appsv1.Deployment)

		dep.Spec.Template.Spec.Containers[0].Image = targetImage(jdk)

		for _, v := range dep.Spec.Template.Spec.Containers[0].Env {
			if _, ok := extraEnvs[v.Name]; !ok {
				extraEnvs[v.Name] = v.Value
			}
		}

		dep.Spec.Template.Spec.Containers[0].Env = []corev1.EnvVar{}
		for k, v := range extraEnvs {
			dep.Spec.Template.Spec.Containers[0].Env = append(dep.Spec.Template.Spec.Containers[0].Env, corev1.EnvVar{
				Name:  k,
				Value: v,
			})
		}
		dep.Spec.Template.Spec.Containers[0].VolumeMounts = []corev1.VolumeMount{
			{
				Name:      "javaagent",
				MountPath: "/opt/javaagent/",
				ReadOnly:  true,
			},
		}
		dep.Spec.Template.Spec.Volumes = []corev1.Volume{
			{
				Name: "javaagent",
				VolumeSource: corev1.VolumeSource{
					EmptyDir: &corev1.EmptyDirVolumeSource{},
				},
			},
		}

		dep.Spec.Template.Spec.InitContainers = []corev1.Container{
			{
				Name:            "inject-javaagent",
				Image:           fmt.Sprintf("%s:%s", *javaloaderImage, *buildTag),
				ImagePullPolicy: corev1.PullIfNotPresent,
				VolumeMounts: []corev1.VolumeMount{
					{
						Name:      "javaagent",
						MountPath: "/opt/javaagent/",
						ReadOnly:  false,
					},
				},
			},
		}

		return nil
	}))
}

func SetupManifest(file, ns, deployment string, mutates ...decoder.DecodeOption) env.Func {
	return func(ctx context.Context, cfg *envconf.Config) (context.Context, error) {

		f, err := os.Open(file)
		if err != nil {
			return ctx, fmt.Errorf("failed to open %s: %w", file, err)
		}

		r, err := resources.New(cfg.Client().RESTConfig())
		if err != nil {
			return ctx, fmt.Errorf("failed to create %s resources: %w", deployment, err)
		}

		detectDeployment := decoder.MutateOption(func(obj k8s.Object) error {
			if deployment != "" {
				return nil
			}
			if obj.GetObjectKind().GroupVersionKind().Kind == "Deployment" {
				deployment = obj.GetName()
			}
			return nil
		})

		mutates = append(mutates, decoder.MutateNamespace(ns), detectDeployment)

		err = decoder.DecodeEach(ctx, f, decoder.CreateHandler(r), mutates...)
		if err != nil {
			return ctx, fmt.Errorf("failed to decode %s manifests: %w", deployment, err)
		}

		dep := appsv1.Deployment{
			ObjectMeta: v1.ObjectMeta{
				Name:      deployment,
				Namespace: ns,
			},
		}
		err = wait.For(conditions.New(cfg.Client().Resources()).ResourceScaled(&dep, func(obj k8s.Object) int32 {
			return obj.(*appsv1.Deployment).Status.ReadyReplicas
		}, 1), wait.WithTimeout(time.Minute*5))
		if err != nil {
			return ctx, fmt.Errorf("failed to wait for %s to be ready: %w", deployment, err)
		}

		return ctx, nil
	}
}

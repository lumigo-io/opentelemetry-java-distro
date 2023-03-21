package k8s

import (
	"flag"
	"fmt"
	"k8s.io/klog/v2"
	"os"
	"sigs.k8s.io/e2e-framework/pkg/env"
	"sigs.k8s.io/e2e-framework/pkg/envconf"
	"sigs.k8s.io/e2e-framework/pkg/envfuncs"
	"testing"
)

var (
	testEnv         env.Environment
	buildTag        *string
	javaloaderImage *string
)

func TestMain(m *testing.M) {
	buildTag = flag.String("build-tag", "", "The tag of the javaloader docker image to use")
	javaloaderImage = flag.String("javaloader-image", "javaagent-loader", "The javaloader name for docker images")
	cfg, _ := envconf.NewFromFlags()

	if *buildTag == "" {
		klog.Fatal("--build-tag argument is required. (or as environment variable BUILD_TAG)")
	}

	testEnv = env.NewWithConfig(cfg)
	kindClusterName := envconf.RandomName("javaagent-test", 20)

	testEnv.Setup(
		envfuncs.CreateKindClusterWithConfig(kindClusterName, "''", "./config/kind-cluster.yaml"),
		envfuncs.LoadDockerImageToCluster(kindClusterName, fmt.Sprintf("%s:%s", *javaloaderImage, *buildTag)),
	)

	testEnv.Finish(
		envfuncs.DestroyKindCluster(kindClusterName),
	)

	os.Exit(testEnv.Run(m))
}

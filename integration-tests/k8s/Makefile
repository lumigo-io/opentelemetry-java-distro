VERSION ?= $(shell git rev-parse --short HEAD)

.DEFAULT_GOAL := help

.PHONY: help
help: ## Display this help.
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Tests

.PHONY: test
test: build-injector ## Run integration tests
	@echo "Running integration tests"
	go test -v -timeout 30m ./... --args -v 5 --build-tag=$(VERSION)

##@ Build

.PHONY: build-injector
build-injector: ## Build injector image
	@echo "Building injector"
	cd ../../ && ./gradlew clean assemble
	docker build -t javaagent-loader:$(VERSION) -t javaagent-loader:latest -f Dockerfile ./../../

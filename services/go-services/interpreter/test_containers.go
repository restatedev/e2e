package interpreter

import (
	"context"
	"fmt"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"
)

type TestEnvironment struct {
	IngressURL string
	AdminURL   string
	Services   []string
	Containers Containers
	Network    testcontainers.Network
}

type Containers struct {
	RestateContainer         testcontainers.Container
	InterpreterZeroContainer testcontainers.Container
	InterpreterOneContainer  testcontainers.Container
	InterpreterTwoContainer  testcontainers.Container
	ServicesContainer        testcontainers.Container
}

func setupContainers(ctx context.Context) (*TestEnvironment, error) {
	network, err := network.New(ctx)
	if err != nil {
		return nil, err
	}

	restateReq := testcontainers.ContainerRequest{
		Image:        "ghcr.io/restatedev/restate:main",
		ExposedPorts: []string{"8080/tcp", "9070/tcp"},
		Networks:     []string{network.Name},
		NetworkAliases: map[string][]string{
			network.Name: {"restate"},
		},
		Env: map[string]string{
			"RESTATE_LOG_FILTER": "restate=warn",
			"RESTATE_LOG_FORMAT": "json",
		},
		WaitingFor: wait.ForListeningPort("8080/tcp"),
	}
	restate, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: restateReq,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	zeroReq := testcontainers.ContainerRequest{
		Image:    "ghcr.io/restatedev/e2e-node-services:main",
		Networks: []string{network.Name},
		NetworkAliases: map[string][]string{
			network.Name: {"interpreter_zero"},
		},
		Env: map[string]string{
			"PORT":            "9000",
			"RESTATE_LOGGING": "ERROR",
			"NODE_ENV":        "production",
			"SERVICES":        "ObjectInterpreterL0",
		},
		WaitingFor: wait.ForListeningPort("9000/tcp"),
	}
	zero, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: zeroReq,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	oneReq := testcontainers.ContainerRequest{
		Image:        "ghcr.io/restatedev/e2e-node-services:main",
		ExposedPorts: []string{"9001/tcp"},
		Networks:     []string{network.Name},
		NetworkAliases: map[string][]string{
			network.Name: {"interpreter_one"},
		},
		Env: map[string]string{
			"PORT":            "9001",
			"RESTATE_LOGGING": "ERROR",
			"NODE_ENV":        "production",
			"SERVICES":        "ObjectInterpreterL1",
		},
		WaitingFor: wait.ForListeningPort("9001/tcp"),
	}
	one, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: oneReq,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	twoReq := testcontainers.ContainerRequest{
		Image:        "ghcr.io/restatedev/e2e-node-services:main",
		ExposedPorts: []string{"9002/tcp"},
		Networks:     []string{network.Name},
		NetworkAliases: map[string][]string{
			network.Name: {"interpreter_two"},
		},
		Env: map[string]string{
			"PORT":            "9002",
			"RESTATE_LOGGING": "ERROR",
			"NODE_ENV":        "production",
			"SERVICES":        "ObjectInterpreterL2",
		},
		WaitingFor: wait.ForListeningPort("9002/tcp"),
	}
	two, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: twoReq,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	servicesReq := testcontainers.ContainerRequest{
		Image:        "ghcr.io/restatedev/e2e-node-services:main",
		ExposedPorts: []string{"9003/tcp"},
		Networks:     []string{network.Name},
		NetworkAliases: map[string][]string{
			network.Name: {"services"},
		},
		Env: map[string]string{
			"PORT":            "9003",
			"RESTATE_LOGGING": "ERROR",
			"NODE_ENV":        "production",
			"SERVICES":        "ServiceInterpreterHelper",
		},
		WaitingFor: wait.ForListeningPort("9003/tcp"),
	}
	services, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: servicesReq,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	host, err := restate.Host(ctx)
	if err != nil {
		return nil, err
	}

	ingressPort, err := restate.MappedPort(ctx, "8080/tcp")
	if err != nil {
		return nil, err
	}

	adminPort, err := restate.MappedPort(ctx, "9070/tcp")
	if err != nil {
		return nil, err
	}

	ingressURL := fmt.Sprintf("http://%s:%s", host, ingressPort.Port())
	adminURL := fmt.Sprintf("http://%s:%s", host, adminPort.Port())

	return &TestEnvironment{
		IngressURL: ingressURL,
		AdminURL:   adminURL,
		Services: []string{
			"http://interpreter_zero:9000",
			"http://interpreter_one:9001",
			"http://interpreter_two:9002",
			"http://services:9003",
		},
		Containers: Containers{
			RestateContainer:         restate,
			InterpreterZeroContainer: zero,
			InterpreterOneContainer:  one,
			InterpreterTwoContainer:  two,
			ServicesContainer:        services,
		},
		Network: network,
	}, nil
}

func tearDown(ctx context.Context, env *TestEnvironment) error {
	if err := env.Containers.RestateContainer.Terminate(ctx); err != nil {
		return err
	}
	if err := env.Containers.InterpreterZeroContainer.Terminate(ctx); err != nil {
		return err
	}
	if err := env.Containers.InterpreterOneContainer.Terminate(ctx); err != nil {
		return err
	}
	if err := env.Containers.InterpreterTwoContainer.Terminate(ctx); err != nil {
		return err
	}
	if err := env.Containers.ServicesContainer.Terminate(ctx); err != nil {
		return err
	}
	return env.Network.Remove(ctx)
}

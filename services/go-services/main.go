package main

import (
	"context"
	"log/slog"
	"os"
	"strings"

	"github.com/restatedev/e2e/services/go-services/interpreter"
	"github.com/restatedev/e2e/services/go-services/services"
	"github.com/restatedev/sdk-go/server"
)

const (
	UserSessionServiceName = "UserSession"
	TicketServiceName      = "TicketService"
	CheckoutServiceName    = "Checkout"
)

func init() {
	interpreter.Register()
}

func main() {
	if os.Getenv("SERVICES") == "" {
		slog.Error("Cannot find SERVICES env")
		os.Exit(1)
	}

	fqdns := strings.Split(os.Getenv("SERVICES"), ",")
	set := make(map[string]struct{}, len(fqdns))
	for _, fqdn := range fqdns {
		set[fqdn] = struct{}{}
	}
	server := server.NewRestate()
	services.REGISTRY.Register(set, server)

	port := os.Getenv("PORT")
	if port == "" {
		port = "9080"
	}

	if err := server.Start(context.Background(), ":"+port); err != nil {
		slog.Error("application exited unexpectedly", "err", err)
		os.Exit(1)
	}
}

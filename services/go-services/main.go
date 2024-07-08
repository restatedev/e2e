package main

import (
	"context"
	"os"
	"strings"

	"github.com/restatedev/e2e/services/go-services/interpreter"
	"github.com/restatedev/e2e/services/go-services/services"
	"github.com/restatedev/sdk-go/server"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

const (
	UserSessionServiceName = "UserSession"
	TicketServiceName      = "TicketService"
	CheckoutServiceName    = "Checkout"
)

func init() {
	interpreter.Register()
	log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr})
	zerolog.SetGlobalLevel(zerolog.DebugLevel)
}

func main() {
	if os.Getenv("SERVICES") == "" {
		log.Fatal().Msg("Cannot find SERVICES env")
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
		log.Fatal().Err(err).Msg("application exited unexpectedly")
		os.Exit(1)
	}
}

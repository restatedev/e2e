package main

import (
	"context"
	"log"
	"os"
	"strings"

	"github.com/restatedev/e2e/services/go-services/services"
	"github.com/restatedev/sdk-go/server"
)

const (
	UserSessionServiceName = "UserSession"
	TicketServiceName      = "TicketService"
	CheckoutServiceName    = "Checkout"
)

func main() {
	if os.Getenv("SERVICES") == "" {
		log.Fatal("Cannot find SERVICES env")
	}

	fqdns := strings.Split(os.Getenv("SERVICES"), ",")
	set := make(map[string]struct{}, len(fqdns))
	for _, fqdn := range fqdns {
		set[fqdn] = struct{}{}
	}
	server := server.NewRestate()
	services.REGISTRY.Register(set, server)

	if err := server.Start(context.Background(), ":9080"); err != nil {
		log.Printf("application exited unexpectedly: %v\n", err)
		os.Exit(1)
	}
}

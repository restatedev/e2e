package services

import (
	"log"

	"github.com/restatedev/sdk-go"
	"github.com/restatedev/sdk-go/server"
)

var REGISTRY = Registry{components: map[string]Component{}}

type Registry struct {
	components map[string]Component
}

type Component struct {
	Fqdn   string
	Binder func(endpoint *server.Restate)
}

func (r *Registry) Add(c Component) {
	r.components[c.Fqdn] = c
}

func (r *Registry) AddRouter(name string, router restate.Router) {
	r.Add(Component{
		Fqdn:   name,
		Binder: func(e *server.Restate) { e.Bind(name, router) },
	})
}

func (r *Registry) Register(fqdns map[string]struct{}, e *server.Restate) {
	for fqdn := range fqdns {
		c, ok := r.components[fqdn]
		if !ok {
			log.Fatalf("unknown fqdn %s. Did you remember to import the test at app.ts?", fqdn)
		}
		c.Binder(e)
	}
}

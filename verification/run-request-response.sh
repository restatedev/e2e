#!/usr/bin/env bash

export SERVICES=InterpreterDriverJob
export NODE_ENV=production
export NODE_OPTIONS="--max-old-space-size=4096"
export E2E_USE_FETCH=true
export DEBUG=testcontainers:containers

SEED=$(date --iso-8601=seconds)


# "image": "ghcr.io/restatedev/e2e-node-services:main",

export INTERPRETER_DRIVER_CONF=$(cat <<-EOF
{
	"seed"	: "${SEED}",
	"keys"	: 100000,
	"tests" : 1000000,
	"maxProgramSize"	:  50,
	"crashInterval"		: 900000,
	"bootstrap"				: true,
	"bootstrapEnv" : {
		"restate": {
					"image": "ghcr.io/restatedev/restate:main",
					"env": {}
		},
		"interpreters" : {
					"image": "e2enode",
					"pull" : false,
					"env": {
						"E2E_USE_FETCH" : ${E2E_USE_FETCH}
					}
		},
		"service": {
					"image": "e2enode",
					"pull" : false,
					"env": {
						"E2E_USE_FETCH" : ${E2E_USE_FETCH}
					}
		}
	}
}
EOF
)

#docker pull ghcr.io/restatedev/e2e-node-services:main 

docker run \
	--net host\
	-v /var/run/docker.sock:/var/run/docker.sock	\
	--env SERVICES	\
	--env E2E_USE_FETCH	\
	--env NODE_ENV \
	--env NODE_OPTIONS \
	--env DEBUG \
	--env INTERPRETER_DRIVER_CONF \
	e2enode 2>&1 | grep -v "undefined is not a number, but it still has feelings"

#	ghcr.io/restatedev/e2e-node-services:main 2>&1 | grep -v "undefined is not a number, but it still has feelings"

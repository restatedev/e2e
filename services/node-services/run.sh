#!/usr/bin/env bash

export SERVICES=InterpreterDriverJob
export NODE_ENV=production
export NODE_OPTIONS="--max-old-space-size=4096"
export AWS_LAMBDA_FUNCTION_NAME=1
export DEBUG=testcontainers:containers

SEED=$(date --iso-8601=seconds)

export INTERPRETER_DRIVER_CONF=$(cat <<-EOF
{
	"seed"	: "${SEED}",
	"keys"	: 100000,
	"tests" : 1000000,
	"maxProgramSize"	:  20,
	"crashInterval"		: 900000,
	"bootstrap"				: true
}
EOF
)


docker run \
	--net host\
	-v /var/run/docker.sock:/var/run/docker.sock	\
	--env SERVICES	\
	--env NODE_ENV \
	--env NODE_OPTIONS \
	--env AWS_LAMBDA_FUNCTION_NAME \
	--env DEBUG \
	--env INTERPRETER_DRIVER_CONF \
	-it e2enode | grep -v "undefined is not a number, but it still has feelings"


#node dist/app.js 2>1 | grep -v "undefined is not a number, but it still has feelings"


#!/usr/bin/env bash

SEED=$(date --iso-8601=seconds)
TIMEOUT_SECNODS=14400
EXPECTED_NOISY_LOG_MESSAGE="undefined is not a number, but it still has feelings"

export INTERPRETER_DRIVER_CONF=$(cat <<-EOF 
			{
        "seed" : "${SEED}", 
        "keys" : 100000,
        "tests" : 1000000,
        "maxProgramSize" : 100,
        "ingress" : "http://restate:8080",
        "register" : {
					"adminUrl" : "http://restate:9070",
					"deployments" : [
														"http://interpreter_zero:9000",
														"http://interpreter_one:9001",
														"http://interpreter_two:9002",
														"http://services:9003"
					]
				}
			}
EOF
)


docker-compose -f compose.template.yml pull


set -o pipefail;

docker-compose -f compose.template.yml up \
	--abort-on-container-exit \
	--exit-code-from driver \
	--force-recreate \
	--timeout ${TIMEOUT_SECNODS} "${@}" | grep -v "${EXPECTED_NOISY_LOG_MESSAGE}"

exit ${PIPESTATUS[0]}



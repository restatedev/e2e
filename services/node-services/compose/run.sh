#!/usr/bin/env bash

SEED=$(date --iso-8601=seconds)
TIMEOUT_SECNODS=3600
EXPECTED_NOISY_LOG_MESSAGE="undefined is not a number, but it still has feelings"

export INTERPRETER_DRIVER_CONF=$(cat <<-EOF 
			{
        "seed" : "${SEED}", 
        "keys" : 10000,
        "tests" : 100000,
        "maxProgramSize" : 15,
        "ingress" : "http://restate:8080",
        "register" : {
          "adminUrl" : "http://restate:9070",
          "deployments" : ["http://interpreter:9080"]
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
	--timeout ${TIMEOUT_SECNODS} | grep -v "${EXPECTED_NOISY_LOG_MESSAGE}"

exit ${PIPESTATUS[0]}



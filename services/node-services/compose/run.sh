#!/usr/bin/env bash

SEED=$(date --iso-8601=seconds)
TIMEOUT_SECNODS=14400
EXPECTED_NOISY_LOG_MESSAGE="undefined is not a number, but it still has feelings"
RESTATE_KILL_SECONDS=1200
COMPOSE_FILE="compose.template.yml"

 
export INTERPRETER_DRIVER_CONF=$(cat <<-EOF 
			{
        "seed" : "${SEED}", 
        "keys" : 100000,
				"tests" : 1000000,
				"maxProgramSize" :  15,
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

follow_compose_log() {
	docker-compose -f "${COMPOSE_FILE}" logs -f | grep -v "${EXPECTED_NOISY_LOG_MESSAGE}" 
}

restart_restate_service() {
	while true; do
		sleep "${RESTATE_KILL_SECONDS}"
		echo "<!> about to restart restate"
		docker-compose -f "${COMPOSE_FILE}" restart restate
	done
}

cleanup() {
    echo "Cleaning up..."
		docker-compose -f "${COMPOSE_FILE}" down --remove-orphans
    pkill -P $$
}

docker-compose -f "${COMPOSE_FILE}" down --remove-orphans
docker-compose -f "${COMPOSE_FILE}" pull
docker-compose -f "${COMPOSE_FILE}" up -d --force-recreate --timeout ${TIMEOUT_SECNODS} "${@}" 
driver_container_id=$(docker-compose -f ${COMPOSE_FILE} ps -q driver 2>/dev/null)

# make sure we kill all the background jobs 
trap cleanup EXIT

follow_compose_log &
restart_restate_service &

# wait for the driver container to exit
while true; do
	driver_status=$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' "${driver_container_id}" 2>/dev/null)
	status_array=($driver_status)
	state=${status_array[0]}
	exit_code=${status_array[1]}
        
	if [ "$state" == "exited" ]; then
		exit $exit_code
	fi
	sleep 1
done


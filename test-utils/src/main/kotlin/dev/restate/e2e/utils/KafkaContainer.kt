package dev.restate.e2e.utils

import com.github.dockerjava.api.command.InspectContainerResponse
import kotlin.check
import org.testcontainers.utility.DockerImageName

/**
 * This class overrides the testcontainers [org.testcontainers.containers.KafkaContainer] to
 * introduce two additional behaviours:
 *
 * * Fix the `advertised.listeners` override with the correct hostname (maybe we can upstream this
 * fix?)
 * * Create topics after the container is started
 */
class KafkaContainer(private vararg val topics: String) :
    org.testcontainers.containers.KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:5.5.1")) {

  init {
    // Make sure we have auto.create.topics.enable as true
    withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
  }

  // This is copied and pasted from KafkaContainer original class to add the listeners from the
  // network aliases and remove the brokerAdvertisedListener
  override fun containerIsStarted(containerInfo: InspectContainerResponse) {
    val currentBrokerAdvertisedAddress = brokerAdvertisedListener(containerInfo)

    // Create topics first
    topics.forEach { topic ->
      val result =
          execInContainer(
              "kafka-topics",
              "--create",
              "--topic",
              topic,
              "--bootstrap-server",
              currentBrokerAdvertisedAddress)
      check(result.exitCode == 0) { result.toString() }
    }

    val listenerList = networkAliases.map { "BROKER://$it:9092" } + bootstrapServers
    val result =
        execInContainer(
            "kafka-configs",
            "--alter",
            "--bootstrap-server",
            currentBrokerAdvertisedAddress,
            "--entity-type",
            "brokers",
            "--entity-name",
            envMap["KAFKA_BROKER_ID"],
            "--add-config",
            "advertised.listeners=[${listenerList.joinToString(separator = ",")}]")
    check(result.exitCode == 0) { result.toString() }
  }
}

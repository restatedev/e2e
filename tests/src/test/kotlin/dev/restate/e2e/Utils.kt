package dev.restate.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

object Utils {

  private val objMapper = ObjectMapper()

  fun jacksonBodyHandler(): HttpResponse.BodyHandler<JsonNode> {
    return HttpResponse.BodyHandler {
      HttpResponse.BodySubscribers.mapping(
          HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), objMapper::readTree)
    }
  }

  fun jacksonBodyPublisher(value: Any): HttpRequest.BodyPublisher {
    return HttpRequest.BodyPublishers.ofString(objMapper.writeValueAsString(value))
  }
}

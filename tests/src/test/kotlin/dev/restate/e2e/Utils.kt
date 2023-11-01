package dev.restate.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

object Utils {

  private val objMapper = ObjectMapper()
  private val httpClient = HttpClient.newHttpClient()

  fun jacksonBodyHandler(): HttpResponse.BodyHandler<JsonNode> {
    return HttpResponse.BodyHandler {
      HttpResponse.BodySubscribers.mapping(
          HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), objMapper::readTree)
    }
  }

  fun jacksonBodyPublisher(value: Any): HttpRequest.BodyPublisher {
    return HttpRequest.BodyPublishers.ofString(objMapper.writeValueAsString(value))
  }

  fun postJsonRequest(uri: String, reqBody: Any): HttpResponse<JsonNode> {
    val req =
        HttpRequest.newBuilder(URI.create(uri))
            .headers("Content-Type", "application/json")
            .POST(jacksonBodyPublisher(reqBody))
            .build()
    return httpClient.send(req, jacksonBodyHandler())
  }
}

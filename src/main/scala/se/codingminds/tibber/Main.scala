package se.codingminds.tibber

import io.prometheus.client.{CollectorRegistry, Gauge}
import io.prometheus.client.exporter.HTTPServer
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.util.StringRequestContent
import org.eclipse.jetty.http.{HttpHeader, HttpMethod}

import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import se.codingminds.tibber.RequestPayloads.{connectionInitMessage, initMessage, subscribeMessage}

import java.net.{InetSocketAddress, URI}
import java.util.concurrent.TimeUnit
import scala.io.Source

object Test {

  val collectorRegistry =
    CollectorRegistry.defaultRegistry

  val log: Logger = LoggerFactory.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {

    setupPrometheus()

    val httpClient = new HttpClient
    httpClient.start()
    getSubscriptionUrl(httpClient)

    val webSocketClient = new WebSocketClient(httpClient)
    webSocketClient.setMaxTextMessageSize(8 * 1024)
    webSocketClient.start()

    val serverURI = URI.create("wss://websocket-api.tibber.com/v1-beta/gql/subscriptions")
    val upgradeRequest = new ClientUpgradeRequest
    upgradeRequest.setHeader(
      "Authorization",
      s"Bearer ${Source.fromResource("token.secret").getLines().mkString}"
    )
    upgradeRequest.setSubProtocols("graphql-transport-ws")
    upgradeRequest.setTimeout(20, TimeUnit.SECONDS)

    scala.sys.addShutdownHook(() -> closeWebSocket(webSocketClient))

    var sessionPromise = webSocketClient.connect(TibberWebSocket, serverURI, upgradeRequest)
  }

  def getSubscriptionUrl(httpClient: HttpClient): String = {
    val response = httpClient
      .newRequest("https://api.tibber.com/v1-beta/gql")
      .method(HttpMethod.POST)
      .headers(header =>
        header
          .put(
            HttpHeader.AUTHORIZATION,
            s"Bearer ${Source.fromResource("token.secret").getLines().mkString}"
          )
      )
      .body(new StringRequestContent("application/json", initMessage))
      .send()

    val json = Json.parse(new String(response.getContent))
    val subscriptionUrl =
      (json \ "data" \ "viewer" \ "websocketSubscriptionUrl").as[String]
    log.info(s"Got subscription URL: ${subscriptionUrl}")
    subscriptionUrl
  }

  def closeWebSocket(webSocketClient: WebSocketClient): Unit = {
    webSocketClient.stop()
    log.info("WebSocket is now closed. Goodbye.")
  }

  def setupPrometheus(): Unit = {
    val address = new InetSocketAddress(9091)
    new HTTPServer(address, collectorRegistry, false)
    log.info(s"Serving Prometheus metrics on port ${address.getPort}")
  }
}

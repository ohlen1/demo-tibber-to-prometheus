package se.codingminds.tibber

import io.prometheus.client.{CollectorRegistry, Gauge}
import io.prometheus.client.exporter.HTTPServer
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.{
  OnWebSocketClose,
  OnWebSocketConnect,
  OnWebSocketError,
  OnWebSocketMessage,
  WebSocket
}
import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json

import java.net.{InetSocketAddress, URI}
import java.util.concurrent.TimeUnit
import scala.io.Source

object Test {

  val collectorRegistry =
    CollectorRegistry.defaultRegistry

  def main(args: Array[String]): Unit = {

    setupPrometheus()

    val httpClient = new HttpClient
    val webSocketClient = new WebSocketClient(httpClient)
    webSocketClient.setMaxTextMessageSize(8 * 1024)
    webSocketClient.start()

    val serverURI = URI.create("wss://websocket-api.tibber.com/v1-beta/gql/subscriptions")
    val upgradeRequest = new ClientUpgradeRequest
    upgradeRequest.setHeader(
      "Authorization",
      s"Bearer ${Source.fromResource("token.secret").getLines.mkString}"
    )
    upgradeRequest.setSubProtocols("graphql-transport-ws")
    upgradeRequest.setTimeout(20, TimeUnit.SECONDS)

    scala.sys.addShutdownHook(() -> closeWebSocket(webSocketClient))

    var sessionPromise = webSocketClient.connect(MySocket, serverURI, upgradeRequest)
  }

  def closeWebSocket(webSocketClient: WebSocketClient): Unit = {
    webSocketClient.stop()
    println("WebSocket is now closed. Goodbye.")
  }

  def setupPrometheus(): Unit = {
    val address = new InetSocketAddress(9091)
    new HTTPServer(address, collectorRegistry, false)
    println(s"Serving Prometheus metrics on port ${address.getPort}")
  }
}

@WebSocket(maxTextMessageSize = 64 * 1024)
object MySocket {

  val log: Logger = LoggerFactory.getLogger(getClass.getName)
  val powerGauge = Gauge
    .build()
    .name("tibber_power_consumption")
    .help("Tibber power consumption gauge")
    .register();

  var session: Session = null
  val connectionInitMessage =
    s"""{
        "type":"connection_init",
        "payload":{
          "token":"${Source.fromResource("token.secret").getLines.mkString}"
         }
        }"""

  val subscribeMessage =
    """{
        "id":"93202458-a20d-4608-b3b6-b56c80662539",
        "type":"subscribe",
        "payload":{
          "variables":{},
          "extensions":{},
          "query":"subscription {\n  liveMeasurement(homeId: \"ef7cc41b-d5b9-492d-adda-43eec97e217d\") {\n    power\n  }\n}"
          }
        }"""

  @OnWebSocketClose
  def onWebSocketClose(statusCode: Int, reason: String): Unit = {
    log.warn("" + statusCode + ":" + reason)
  }

  @OnWebSocketConnect
  def onWebSocketConnect(newSession: Session): Unit = {
    log.info(s"Got connect: ${newSession}")
    log.info(s"Status code: ${newSession.getUpgradeResponse.getStatusCode}")
    session = newSession

    try {
      log.info(s"Sending init: ${connectionInitMessage}")
      newSession.getRemote.sendString(connectionInitMessage)
      log.info("Init sent")

      log.info(s"Sending subscribe: ${subscribeMessage}")
      newSession.getRemote.sendString(subscribeMessage)
      log.info("Subscribe sent")
    } catch {
      case t: Throwable =>
        t.printStackTrace()
    }
  }

  @OnWebSocketMessage
  def onWebSocketMessage(message: String): Unit = {
    println(s"Got msg: ${message}")
    val json = Json.parse(message)
    if ((json \ "type").as[String].equals("next")) {
      val power = (json \ "payload" \ "data" \ "liveMeasurement" \ "power").as[Int]
      log.info(s"Power: ${power}")
      powerGauge.set(power)
    }
  }

  @OnWebSocketError
  def onWebSocketError(cause: Throwable): Unit = {
    log.error("WebSocket Error: ")
    cause.printStackTrace(System.out)
  }
}

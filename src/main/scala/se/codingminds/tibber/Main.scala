package se.codingminds.tibber

import io.prometheus.client.{CollectorRegistry, Gauge}
import io.prometheus.client.exporter.HTTPServer
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.util.StringRequestContent
import org.eclipse.jetty.http.{HttpHeader, HttpMethod}
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

    var sessionPromise = webSocketClient.connect(MySocket, serverURI, upgradeRequest)
  }

  def getSubscriptionUrl(httpClient: HttpClient): String = {
    val response = httpClient
      .newRequest("https://api.tibber.com/v1-beta/gql")
      .method(HttpMethod.POST)
      .headers(headers =>
        headers
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

@WebSocket(maxTextMessageSize = 64 * 1024)
object MySocket {

  val log: Logger = LoggerFactory.getLogger(getClass.getName)

  var session: Session = null

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
      log.debug(s"Power: ${power}")
      Metrics.currentPowerGauge.set(power)

      val minPower = (json \ "payload" \ "data" \ "liveMeasurement" \ "minPower").as[Int]
      log.debug(s"Min power: ${minPower}")

      val maxPower = (json \ "payload" \ "data" \ "liveMeasurement" \ "maxPower").as[Int]
      log.debug(s"Max power: ${maxPower}")

      val avgPower = (json \ "payload" \ "data" \ "liveMeasurement" \ "averagePower").as[Double]
      log.debug(s"Avg power: ${avgPower}")

      val currentPhase1 = (json \ "payload" \ "data" \ "liveMeasurement" \ "currentL1").as[Double]
      log.debug(s"Current P1: ${currentPhase1}")
      Metrics.currentPhaseCurrent.labels("1").set(currentPhase1)

      val currentPhase2 = (json \ "payload" \ "data" \ "liveMeasurement" \ "currentL2").as[Double]
      log.debug(s"Current P2: ${currentPhase2}")
      Metrics.currentPhaseCurrent.labels("2").set(currentPhase2)

      val currentPhase3 = (json \ "payload" \ "data" \ "liveMeasurement" \ "currentL3").as[Double]
      log.debug(s"Current P3: ${currentPhase3}")
      Metrics.currentPhaseCurrent.labels("3").set(currentPhase3)

      val voltagePhase1 =
        (json \ "payload" \ "data" \ "liveMeasurement" \ "voltagePhase1").as[Double]
      log.debug(s"Voltage P1: ${voltagePhase1}")
      Metrics.currentPhaseVoltage.labels("1").set(voltagePhase1)

      val voltagePhase2 =
        (json \ "payload" \ "data" \ "liveMeasurement" \ "voltagePhase2").as[Double]
      log.debug(s"Voltage P2: ${voltagePhase2}")
      Metrics.currentPhaseVoltage.labels("2").set(voltagePhase2)

      val voltagePhase3 =
        (json \ "payload" \ "data" \ "liveMeasurement" \ "voltagePhase3").as[Double]
      log.debug(s"Voltage P3: ${voltagePhase3}")
      Metrics.currentPhaseVoltage.labels("3").set(voltagePhase3)
    }
  }

  @OnWebSocketError
  def onWebSocketError(cause: Throwable): Unit = {
    log.error("WebSocket Error: ")
    cause.printStackTrace(System.out)
  }
}

object Metrics {
  val currentPowerGauge = Gauge
    .build()
    .name("tibber_current_power_consumption")
    .help("Current power consumption gauge")
    .register();

  val currentPhaseCurrent = Gauge
    .build()
    .name("tibber_current_phase_current")
    .labelNames("phase_no")
    .help("Current phase current")
    .register();

  val currentPhaseVoltage = Gauge
    .build()
    .name("tibber_current_phase_voltage")
    .labelNames("phase_no")
    .help("Current phase votage")
    .register();
}

object RequestPayloads {

  val initMessage =
    """
    {
      "query": "{\n  viewer {\n    websocketSubscriptionUrl\n  }\n}"
    }
    """

  val connectionInitMessage =
    s"""{
        "type":"connection_init",
        "payload":{
          "token":"${Source.fromResource("token.secret").getLines().mkString}"
         }
        }"""

  val subscribeMessage =
    """{
        "id":"93202458-a20d-4608-b3b6-b56c80662539",
        "type":"subscribe",
        "payload":{
          "variables":{},
          "extensions":{},
          "query":"subscription {\n  liveMeasurement(homeId: \"ef7cc41b-d5b9-492d-adda-43eec97e217d\") {\n    power\n    minPower\n    maxPower\n    averagePower\n    currentL1\n    currentL2\n    currentL3\n    voltagePhase1\n    voltagePhase2\n    voltagePhase3\n  }\n}"
          }
        }"""
}

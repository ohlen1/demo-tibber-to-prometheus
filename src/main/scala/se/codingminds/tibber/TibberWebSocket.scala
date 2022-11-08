package se.codingminds.tibber

import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.{OnWebSocketClose, OnWebSocketConnect, OnWebSocketError, OnWebSocketMessage, WebSocket}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import se.codingminds.tibber.RequestPayloads.{connectionInitMessage, subscribeMessage}

@WebSocket(maxTextMessageSize = 64 * 1024)
object TibberWebSocket {

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

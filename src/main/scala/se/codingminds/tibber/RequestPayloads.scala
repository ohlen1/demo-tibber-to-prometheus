package se.codingminds.tibber

import scala.io.Source

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

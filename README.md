# Tibber to Prometheus with Scala Demo
Simple app demonstrating how to consume Tibber GraphQL subscription endpoint using the [Jetty Websocket Client](https://www.eclipse.org/jetty/documentation/jetty-11/programming-guide/index.html#pg-client-websocket).

[play-json](https://index.scala-lang.org/playframework/play-json) is used to deal with JSON.

## Usage
1. Create file `src/main/resources/token.secret` and write your personal Tibber as the only contents of the file
2. Start Prometheus. Use `prometheus.yaml` file that resides in the project root
   ```
   prometheus --config.file=prometheus.yaml
   ```
3. Start the demo app
   ```
   ./gradle run
   ```
   
## References
* [Tibber API](https://developer.tibber.com/docs/overview)
* [Jetty WebSocket Client](https://www.eclipse.org/jetty/documentation/jetty-11/programming-guide/index.html#pg-client-websocket)
* [Scala Play JSON](https://index.scala-lang.org/playframework/play-json)
* [WebSocket Status Codes Cheat Sheet](https://kapeli.com/cheat_sheets/WebSocket_Status_Codes.docset/Contents/Resources/Documents/index)
* [Prometheus Getting Started](https://prometheus.io/docs/prometheus/latest/getting_started/)
* [Prometheus Client for Java](https://github.com/prometheus/client_java)

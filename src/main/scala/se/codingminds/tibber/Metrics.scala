package se.codingminds.tibber

import io.prometheus.client.Gauge

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

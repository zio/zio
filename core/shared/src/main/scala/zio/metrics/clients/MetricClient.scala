package zio.metrics.clients

import zio.internal.metrics._

/**
 * A `MetricClient` provides the functionality to consume metrics produced by
 * ZIO applications. `MetricClient` supports two ways of consuming metrics,
 * corresponding to the two ways that third party metrics services use metrics.
 *
 * First, metrics services can poll for the current state of all recorded
 * metrics using the `unsafeSnapshot` method, which provides a snapshot, as of
 * a point in time, of all metrics recorded by the ZIO application.
 *
 * Second, metrics services can install a listener that will be notified every
 * time a metric is updated.
 *
 * `MetricClient` is a lower level interface and is intended to be used by
 * implementers of integrations with third party metrics services but not by
 * end users.
 */
object MetricClient {

  /**
   * Unsafely installs the specified metric listener.
   */
  final def unsafeInstallListener(listener: MetricListener): Unit =
    metricState.installListener(listener)

  /**
   * Unsafely removed the specified metric listener.
   */
  final def unsafeRemoveListener(listener: MetricListener): Unit =
    metricState.removeListener(listener)

  /**
   * Unsafely captures a snapshot of all metrics recorded by the application.
   */
  final def unsafeSnapshot: Map[MetricKey, MetricState] =
    metricState.snapshot
}

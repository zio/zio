package zio.metrics

final case class MetricPair[A, In, Out](metricKey: MetricKey[A, In, Out], metricState: MetricState[A])

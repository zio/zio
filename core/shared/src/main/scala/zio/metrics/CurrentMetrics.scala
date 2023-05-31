package zio.metrics

import zio.internal.metrics.metricRegistry
import zio.metrics.CurrentMetrics.MetricsRenderer
import zio.{IO, Trace, UIO, ZIO}

import java.io.IOException

final case class CurrentMetrics private[metrics] (metrics: Set[MetricPair.Untyped]) extends Serializable {
  def prettyPrint(implicit trace: Trace): UIO[String] =
    MetricsRenderer.prettyPrint(metrics)
}

object CurrentMetrics {

  /**
   * Gets current metrics snapshot.
   */
  def snapshot(): UIO[CurrentMetrics] =
    ZIO.succeedUnsafe { implicit u =>
      CurrentMetrics(metricRegistry.snapshot())
    }

  /**
   * Dumps all current metrics to the specified callback.
   */
  def dumpWith[R, E, T](f: CurrentMetrics => ZIO[R, E, T])(implicit trace: Trace): ZIO[R, E, T] =
    snapshot().flatMap(f)

  /**
   * Dumps all current metrics to the console.
   */
  def dump(implicit trace: Trace): IO[IOException, Unit] =
    dumpWith(_.prettyPrint.flatMap(zio.Console.print(_)))

  private[metrics] object MetricsRenderer {

    def prettyPrint(metrics: Set[MetricPair.Untyped])(implicit trace: Trace): UIO[String] =
      zio.System.lineSeparator.map(renderMetrics(metrics, _))

    private def renderMetrics(metrics: Set[MetricPair.Untyped], lineSeparator: String): String =
      if (metrics.nonEmpty) {

        val maxNameLength       = metrics.map(_.metricKey.name.length).max + 2
        val maxTagSectionLength = metrics.map(m => tagsToString(m.metricKey.tags).length).max + 2

        metrics
          .groupBy(_.metricKey.name)
          .toList
          .sortBy(_._1)
          .map { case (_, groupedMetrics) =>
            groupedMetrics
              .map(metric =>
                renderKey(metric.metricKey, maxNameLength) +
                  renderTags(metric.metricKey, maxTagSectionLength) +
                  renderValue(metric)
              )
              .mkString(lineSeparator)
          }
          .mkString(lineSeparator)

      } else ""

    private def renderKey(key: MetricKey[_], padTo: Int): String =
      key.name.padTo(padTo, ' ')

    private def renderTags(key: MetricKey[_], padTo: Int): String = {
      val tagsStr = tagsToString(key.tags)
      tagsStr + " " * math.max(0, padTo - tagsStr.length)
    }

    private def renderValue(metric: MetricPair[_, _]): String = {
      def renderKeyValues(keyValues: Iterable[(Any, Any)]): String =
        keyValues.map(p => s"(${p._1} -> ${p._2})").mkString(", ")

      metric.metricState match {
        case MetricState.Counter(count) => s"Counter[$count]"
        case MetricState.Frequency(occurrences) =>
          s"Frequency[${renderKeyValues(occurrences)}]"
        case MetricState.Gauge(value) => s"Gauge[$value]"
        case MetricState.Histogram(buckets, count, min, max, sum) =>
          s"Histogram[buckets: [${renderKeyValues(buckets)}], count: [$count], min: [$min], max: [$max], sum: [$sum]]"
        case MetricState.Summary(_, quantiles, count, min, max, sum) =>
          s"Summary[quantiles: [${renderKeyValues(quantiles)}], count: [$count], min: [$min], max: [$max], sum: [$sum]]"
      }
    }

    private def tagsToString(tags: Set[MetricLabel]): String = {
      val byName = tags.toList.sortBy(_.key)
      "tags[" + byName.map(l => s"${l.key}: ${l.value}").mkString(", ") + "]"
    }

  }
}

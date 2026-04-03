package zio.metrics

import zio.{IO, Trace, UIO}

import java.io.IOException

final case class Metrics private[zio] (metrics: Set[MetricPair.Untyped]) {

  /**
   * Gets all current metrics in pretty debug view.
   */
  def prettyPrint(implicit trace: Trace): UIO[String] =
    zio.System.lineSeparator.map(renderMetrics(metrics, _))

  /**
   * Dumps all current metrics to the console.
   */
  def dump(implicit trace: Trace): IO[IOException, Unit] =
    prettyPrint.flatMap(zio.Console.printLine(_))

  private def renderMetrics(metrics: Set[MetricPair.Untyped], lineSeparator: String): String =
    if (metrics.nonEmpty) {

      val maxNameLength =
        metrics.map(m => m.metricKey.name.length + m.metricKey.description.map(_.length + 2).getOrElse(0)).max + 2
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
    s"${key.name}${key.description.map(d => s"($d)").getOrElse("")}".padTo(padTo, ' ')

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

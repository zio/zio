/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.metrics

import zio._
import zio.internal.metrics._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `MetricClient` provides the functionality to consume metrics produced by
 * ZIO applications. `MetricClient` supports two ways of consuming metrics,
 * corresponding to the two ways that third party metrics services use metrics.
 *
 * First, metrics services can query the latest snapshot that has been extracted
 * from the underlying metric registry.
 *
 * Second, metrics services can install a listener that will be called with the
 * complete set of metrics whenever it has been updated from the underlying
 * registry.
 *
 * The default implementation abstracts the underlying, performance optimized
 * API so that it is easier to implement arbitrary metric backends. The default
 * implementation queries the underlying metric registry on a regular basis and
 * caches the result, so that subsequent snapshot() calls will not NOT trigger
 * any activity on the metric registry itself.
 *
 * As a consequence, calls to snapshot() will not yield the most recent value.
 * From a users perspective this should be acceptable as most metric backends
 * operate on a scheduled refresh interval.
 */
trait MetricClient {

  /**
   * Get the most recent snapshot. Tis method is typically used by backend
   * implementations that require the current metric state on demand - such as
   * Prometheus.
   */
  def snapshot(implicit trace: ZTraceElement): UIO[Set[MetricPair.Untyped]]

  /**
   * Register a new listener that can consume metrics. The most common use case
   * is to push these metrics to a backend in the backend specific format.
   */
  def registerListener(listener: MetricListener)(implicit trace: ZTraceElement): UIO[Unit]

  /**
   * Deregister a metric listener.
   */
  def deregisterListener(listener: MetricListener)(implicit trace: ZTraceElement): UIO[Unit]
}

object MetricClient {

  final case class Settings(
    pollingInterval: Duration
  )

  object Settings {
    val default = Settings(
      10.seconds
    )
  }

  class ZIOMetricClient private[MetricClient] (
    settings: MetricClient.Settings,
    listeners: Ref[Chunk[MetricListener]],
    latestSnapshot: Ref[Set[MetricPair.Untyped]]
  ) extends MetricClient {

    def deregisterListener(l: MetricListener)(implicit trace: ZTraceElement): UIO[Unit] =
      listeners.update(cur => cur :+ l)

    def registerListener(l: MetricListener)(implicit trace: ZTraceElement): UIO[Unit] =
      listeners.update(cur => cur.filterNot(_.equals(l)))

    def snapshot(implicit trace: ZTraceElement): UIO[Set[MetricPair.Untyped]] =
      latestSnapshot.get

    private def update(implicit trace: ZTraceElement): UIO[Unit] = for {
      next       <- retrieveNext
      registered <- listeners.get
      _          <- latestSnapshot.set(next)
      _          <- ZIO.foreachPar(registered)(l => l.update(next))
    } yield ()

    private def retrieveNext(implicit
      trace: ZTraceElement
    ): UIO[Set[MetricPair.Untyped]] = for {
      // first we get the state for all the counters that we had captured in the last run
      cnt <- latestSnapshot.get.map(old => counters(old))
      // then we get the snapshot from the underlying metricRegistry
      next = metricRegistry.snapshot()
      res  = snapshotWithDeltas(cnt, next)
    } yield res

    // This will produce a map of all counters in a set of metrics and their state
    private def counters(metrics: Set[MetricPair.Untyped]): Map[MetricKey.Counter, MetricState.Counter] = {

      val builder = scala.collection.mutable.Map[MetricKey.Counter, MetricState.Counter]()
      val it      = metrics.iterator
      while (it.hasNext) {
        val e = it.next()
        e.metricState match {
          case c: MetricState.Counter => builder.update(e.metricKey.asInstanceOf[MetricKey.Counter], c)
          case _                      => // do nothing
        }
      }

      builder.toMap
    }

    // take a map of counters and a set of metrics, update all counters in the set with a delta
    // nexCounterValue - oldCounterValue
    private def snapshotWithDeltas(
      oldCounters: Map[MetricKey.Counter, MetricState.Counter],
      metrics: Set[MetricPair.Untyped]
    ): Set[MetricPair.Untyped] =
      metrics.map { mp =>
        mp.metricState match {
          case c: MetricState.Counter =>
            val lastValue = oldCounters.get(mp.metricKey.asInstanceOf[MetricKey.Counter]).map(_.count).getOrElse(0d)
            MetricPair.unsafeMake(mp.metricKey, c.copy(delta = c.count - lastValue))
          case o => mp
        }
      }

    def run(implicit trace: ZTraceElement): UIO[Unit] =
      update.schedule(Schedule.fixed(settings.pollingInterval)).forkDaemon.unit

  }

  def live(implicit trace: ZTraceElement): ZLayer[Settings, Nothing, MetricClient] = ZLayer.fromZIO(
    for {
      settings  <- ZIO.service[Settings]
      listeners <- Ref.make[Chunk[MetricListener]](Chunk.empty)
      snapshot  <- Ref.make(Set.empty[MetricPair.Untyped])
    } yield new ZIOMetricClient(settings, listeners, snapshot)
  )
}

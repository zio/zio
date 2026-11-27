package zio.metrics.jvm

import zio._
import zio.metrics._

import java.lang.management.{ManagementFactory, ThreadMXBean}

/**
 * The polling metrics of the JVM threads (`ThreadMXBean`). Each one is a gauge.
 *
 * @param threadsCurrent
 *   `jvm_threads_current`: the number of live threads
 * @param threadsDaemon
 *   `jvm_threads_daemon`: the number of live daemon threads
 * @param threadsPeak
 *   `jvm_threads_peak`: the peak number of live threads
 * @param threadsStartedTotal
 *   `jvm_threads_started_total`: the number of threads started since the JVM
 *   started
 * @param threadsDeadlocked
 *   `jvm_threads_deadlocked`: the number of threads that are deadlocked waiting
 *   for object monitors or ownable synchronizers (`findDeadlockedThreads`)
 * @param threadsDeadlockedMonitor
 *   `jvm_threads_deadlocked_monitor`: polled with `findDeadlockedThreads`, the
 *   same call as `threadsDeadlocked`, so it reports the same number
 */
final case class Thread(
  threadsCurrent: PollingMetric[Any, Throwable, MetricState.Gauge],
  threadsDaemon: PollingMetric[Any, Throwable, MetricState.Gauge],
  threadsPeak: PollingMetric[Any, Throwable, MetricState.Gauge],
  threadsStartedTotal: PollingMetric[Any, Throwable, MetricState.Gauge],
  threadsDeadlocked: PollingMetric[Any, Throwable, MetricState.Gauge],
  threadsDeadlockedMonitor: PollingMetric[Any, Throwable, MetricState.Gauge]
)

/**
 * The layer that collects the thread metrics.
 */
object Thread {

  /** Current count of threads by state */
  private def threadsState(state: java.lang.Thread.State): Metric[MetricKeyType.Gauge, Long, MetricState.Gauge] =
    Metric.gauge("jvm_threads_state").tagged(MetricLabel("state", state.name())).contramap[Long](_.toDouble)

  private def getThreadStateCounts(
    threadMXBean: ThreadMXBean
  ): Task[Map[java.lang.Thread.State, Long]] =
    for {
      allThreads <- ZIO.attempt(threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds, 0))
      initial     = java.lang.Thread.State.values().map(_ -> 0L).toMap
      result = allThreads.foldLeft(initial) { (result, thread) =>
                 if (thread ne null) {
                   result.updated(thread.getThreadState, result(thread.getThreadState) + 1)
                 } else result
               }
    } yield result

  private def refreshThreadStateCounts(threadMXBean: ThreadMXBean) =
    for {
      threadStateCounts <- getThreadStateCounts(threadMXBean)
      _ <- ZIO.foreachDiscard(threadStateCounts) { case (state, count) =>
             threadsState(state).set(count)
           }
    } yield ()

  /**
   * A layer that polls the thread metrics on the `updateMetrics` schedule of
   * [[JvmMetricsSchedule]], until the layer's scope closes. On the same
   * schedule it also sets the gauge `jvm_threads_state`, tagged with `state`,
   * to the number of threads in each `java.lang.Thread.State`.
   */
  val live: ZLayer[JvmMetricsSchedule, Throwable, Thread] =
    ZLayer.scoped {
      for {
        threadMXBean <- ZIO.attempt(ManagementFactory.getThreadMXBean)
        threadsCurrent =
          PollingMetric(
            Metric
              .gauge("jvm_threads_current")
              .contramap[Int](_.toDouble),
            ZIO.attempt(threadMXBean.getThreadCount)
          )
        threadsDaemon =
          PollingMetric(
            Metric
              .gauge("jvm_threads_daemon")
              .contramap[Int](_.toDouble),
            ZIO.attempt(threadMXBean.getDaemonThreadCount)
          )
        threadsPeak =
          PollingMetric(
            Metric
              .gauge("jvm_threads_peak")
              .contramap[Int](_.toDouble),
            ZIO.attempt(threadMXBean.getPeakThreadCount)
          )
        threadsStartedTotal =
          PollingMetric(
            Metric
              .gauge("jvm_threads_started_total")
              .contramap[Long](_.toDouble),
            ZIO.attempt(threadMXBean.getTotalStartedThreadCount)
          )
        threadsDeadlocked =
          PollingMetric(
            Metric
              .gauge("jvm_threads_deadlocked")
              .contramap[Int](_.toDouble),
            ZIO.attempt(Option(threadMXBean.findDeadlockedThreads()).map(_.length).getOrElse(0))
          )
        threadsDeadlockedMonitor =
          PollingMetric(
            Metric
              .gauge("jvm_threads_deadlocked_monitor")
              .contramap[Int](_.toDouble),
            ZIO.attempt(Option(threadMXBean.findDeadlockedThreads()).map(_.length).getOrElse(0))
          )

        schedule <- ZIO.service[JvmMetricsSchedule]
        _        <- threadsCurrent.launch(schedule.updateMetrics)
        _        <- threadsDaemon.launch(schedule.updateMetrics)
        _        <- threadsPeak.launch(schedule.updateMetrics)
        _        <- threadsStartedTotal.launch(schedule.updateMetrics)
        _        <- threadsDeadlocked.launch(schedule.updateMetrics)
        _        <- threadsDeadlockedMonitor.launch(schedule.updateMetrics)
        _        <- refreshThreadStateCounts(threadMXBean).scheduleFork(schedule.updateMetrics)
      } yield Thread(
        threadsCurrent,
        threadsDaemon,
        threadsPeak,
        threadsStartedTotal,
        threadsDeadlocked,
        threadsDeadlockedMonitor
      )
    }
}

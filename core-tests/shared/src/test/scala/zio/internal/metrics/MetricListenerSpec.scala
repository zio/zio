package zio.internal.metrics

import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics.MetricKeyType.{Counter, Frequency, Gauge}
import zio.metrics._
import zio.test.Assertion._
import zio.test.{Spec, _}
import zio.{ZIOBaseSpec, _}

import java.time.Instant

object MetricListenerSpec extends ZIOBaseSpec {

  case class HistogramListener(queue: Queue[(MetricKey[MetricKeyType.Histogram], Double)], runtime: Runtime[Any])
      extends MetricListener {
    override def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit
      unsafe: Unsafe
    ): Unit = {
      val _ = runtime.unsafe.run(queue.offer((key, value)))
    }

    override def updateGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

    override def updateFrequency(key: MetricKey[Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

    override def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: Instant)(implicit
      unsafe: Unsafe
    ): Unit = ()

    override def updateCounter(key: MetricKey[Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()
  }

  override def spec: Spec[Environment, Any] =
    suite("MetricListenerSpec")(
      test("listeners get notified") {
        ZIO.scoped(
          for {
            listenerQueue <- Queue.bounded[(MetricKey[MetricKeyType.Histogram], Double)](1)
            runtime       <- ZIO.runtime[Any]
            listener       = HistogramListener(listenerQueue, runtime)
            _ <- ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)))(_ =>
                   ZIO.succeed(MetricClient.removeListener(listener))
                 )
            metric       = Metric.histogram("test", Boundaries(Chunk.empty))
            _           <- ZIO.succeed(3.3) @@ metric
            event       <- listenerQueue.take
            (key, value) = event
          } yield assert(key.name)(equalTo("test")) && assert(value)(equalTo(3.3))
        )
      },
      test("can remove listeners") {
        for {
          listenerQueue <- Queue.bounded[(MetricKey[MetricKeyType.Histogram], Double)](1)
          runtime       <- ZIO.runtime[Any]
          listener       = HistogramListener(listenerQueue, runtime)
          _              = MetricClient.addListener(listener)
          _              = MetricClient.removeListener(listener)
          metric         = Metric.histogram("test", Boundaries(Chunk.empty))
          _             <- ZIO.succeed(3.3) @@ metric
          isEmpty       <- listenerQueue.isEmpty
        } yield assert(isEmpty)(isTrue)
      }
    )

}

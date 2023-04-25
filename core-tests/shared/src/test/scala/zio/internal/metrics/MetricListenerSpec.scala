package zio.internal.metrics

import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics.MetricKeyType.{Counter, Frequency, Gauge}
import zio.metrics._
import zio.test.Assertion._
import zio.test.{Spec, _}
import zio.{ZIOBaseSpec, _}

import java.time.Instant

object MetricListenerSpec extends ZIOBaseSpec {

  case class HistogramListener(
    promise: Promise[Nothing, (MetricKey[MetricKeyType.Histogram], Double)],
    runtime: Runtime[Any]
  ) extends MetricListener {
    override def modifyGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

    override def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit
      unsafe: Unsafe
    ): Unit = {
      val _ = runtime.unsafe.run(promise.succeed((key, value)))
    }

    override def updateGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

    override def updateFrequency(key: MetricKey[Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

    override def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: Instant)(implicit
      unsafe: Unsafe
    ): Unit = ()

    override def updateCounter(key: MetricKey[Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()
  }

  case class GaugeListener(
    ref: Ref[Double],
    runtime: Runtime[Any]
  ) extends MetricListener {
    override def modifyGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = {
      val _ = runtime.unsafe.run(ref.update(_ + value))
    }

    override def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit
      unsafe: Unsafe
    ): Unit = ()

    override def updateGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = {
      val _ = runtime.unsafe.run(ref.set(value))
    }

    override def updateFrequency(key: MetricKey[Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

    override def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: Instant)(implicit
      unsafe: Unsafe
    ): Unit = ()

    override def updateCounter(key: MetricKey[Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()
  }

  override def spec: Spec[Environment, Any] =
    suite("MetricListenerSpec")(
      test("listeners get notified") {
        Unsafe.unsafe { implicit unsafe =>
          ZIO.scoped(
            for {
              listenerPromise <- Promise.make[Nothing, (MetricKey[MetricKeyType.Histogram], Double)]
              runtime         <- ZIO.runtime[Any]
              listener         = HistogramListener(listenerPromise, runtime)
              _ <- ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)))(_ =>
                     ZIO.succeed(MetricClient.removeListener(listener))
                   )
              metric       = Metric.histogram("test", Boundaries(Chunk.empty))
              _           <- ZIO.succeed(3.3) @@ metric
              event       <- listenerPromise.await
              (key, value) = event
            } yield assert(key.name)(equalTo("test")) && assert(value)(equalTo(3.3))
          )
        }
      },
      test("can remove listeners") {
        Unsafe.unsafe { implicit unsafe =>
          for {
            listenerPromise <- Promise.make[Nothing, (MetricKey[MetricKeyType.Histogram], Double)]
            runtime         <- ZIO.runtime[Any]
            listener         = HistogramListener(listenerPromise, runtime)
            _                = MetricClient.addListener(listener)
            _                = MetricClient.removeListener(listener)
            metric           = Metric.histogram("test", Boundaries(Chunk.empty))
            _               <- ZIO.succeed(3.3) @@ metric
            isDone          <- listenerPromise.isDone
          } yield assert(isDone)(isFalse)
        }
      },
      test("gauge") {
        Unsafe.unsafe { implicit unsafe =>
          ZIO.scoped(
            for {
              listenerRef <- Ref.make(0.0)
              runtime     <- ZIO.runtime[Any]
              listener     = GaugeListener(listenerRef, runtime)
              _ <- ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)))(_ =>
                     ZIO.succeed(MetricClient.removeListener(listener))
                   )
              metric = Metric.gauge("test")
              _     <- metric.set(2.0)
              _     <- metric.incrementBy(3.0)
              value <- listenerRef.get
            } yield assert(value)(equalTo(5.0))
          )
        }
      }
    ) @@ TestAspect.sequential

}

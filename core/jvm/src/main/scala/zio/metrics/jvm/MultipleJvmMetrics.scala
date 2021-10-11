package zio.metrics.jvm

import zio.{Clock, Has, NonEmptyChunk, System, ZIOApp, ZManaged, ZTraceElement}
import zio.internal.stacktracer.Tracer

/** Base trait for managing multiple JvmMetrics collectors together */
trait MultipleJvmMetrics {
  protected def collectors(implicit trace: ZTraceElement): NonEmptyChunk[JvmMetrics]

  /**
   * While acquired it starts fibers periodically updating the same JVM metrics
   * as the Prometheus Java client's default exporters
   */
  def collectDefaultJvmMetrics(implicit trace: ZTraceElement): ZManaged[Has[Clock] with Has[System], Throwable, Unit] =
    ZManaged.foreachParDiscard(collectors)(_.collectMetrics)

  /** A ZIO application that collects the same JVM metrics as the Prometheus Java client's default exporters. */
  lazy val app: ZIOApp = {
    implicit val trace: ZTraceElement = Tracer.newTrace
    collectors.tail.map(_.app).foldLeft(collectors.head.app)(_ <> _)
  }

}

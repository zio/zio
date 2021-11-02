package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class ZIOSpecDefault(implicit trace: ZTraceElement) extends ZIOSpec[TestEnvironment] {
  def spec: ZSpec[TestEnvironment, Any]

  override val layer: ZLayer[Has[ZIOAppArgs], Any, TestEnvironment] =
    zio.ZEnv.live >>> TestEnvironment.live

}

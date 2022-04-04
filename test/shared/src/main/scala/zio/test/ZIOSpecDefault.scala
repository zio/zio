package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.Tracer.{instance, newTrace}
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class ZIOSpecDefault extends ZIOSpec[TestEnvironment] {

  def layer: ZLayer[ZIOAppArgs with Scope, Any, TestEnvironment] = {
    implicit val trace: zio.ZTraceElement = Tracer.newTrace
    zio.ZEnv.live >>> TestEnvironment.live
  }

  def spec: ZSpec[TestEnvironment with Scope, Any]
}

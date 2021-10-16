package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.environment._

abstract class ZIOSpecDefault extends ZIOSpec[TestEnvironment] {

  final val testLayer: ZLayer[TestEnvironment, Any, TestEnvironment] = ZLayer.environment(Tracer.newTrace)

  def spec: ZSpec[TestEnvironment, Any]
}

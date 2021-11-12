package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class ZIOSpecDefault extends ZIOSpec[TestEnvironment] {

  final val testDeps: ZDeps[TestEnvironment, Any, TestEnvironment] = ZDeps.environment(Tracer.newTrace)

  def spec: ZSpec[TestEnvironment, Any]
}

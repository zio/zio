package zio.test

import zio._
import zio.internal.stacktracer.Tracer

/**
 * Bootstrap layer for Native platform. Currently just uses the default test
 * environment since custom executors are only supported on JVM.
 */
private[test] object BootstrapLayerConfigSpecific {

  def make: ZLayer[Any, Any, TestEnvironment] = {
    implicit val trace: Trace = Tracer.newTrace
    testEnvironment
  }
}

package zio.test

import zio._
import zio.test.environment._

abstract class ZIOSpecDefault extends ZIOSpec[TestEnvironment] {

  final val layer: ZLayer[TestEnvironment, Any, TestEnvironment] = ZLayer.environment

  def spec: ZSpec[TestEnvironment, Any]
}

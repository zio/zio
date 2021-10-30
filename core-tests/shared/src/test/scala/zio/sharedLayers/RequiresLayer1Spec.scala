package zio.sharedLayers

import zio.test._
import zio.test.environment.Live
import zio.{Has, ZIOAppArgs, ZLayer}

import java.io.IOException

object RequiresLayer1Spec extends ZIOSpec[Has[Unit]] {

  def spec: Spec[ZTestEnv with Has[Live], TestFailure[
    Any
  ], TestSuccess] =
    test("depends on a shared layer 1") {
      assertCompletes
    }
  override def layer: ZLayer[Has[ZIOAppArgs], IOException, Has[Unit]] =
    SharedLayer.test

}

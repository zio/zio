package zio.sharedLayers

import zio.test.environment.Live
import zio.test._
import zio.{Has, ZIOAppArgs, ZLayer}

import java.io.IOException

object RequiresLayer2Spec extends ZIOSpec[Has[Unit]] {

  def spec: Spec[ZTestEnv with Has[Live], TestFailure[
    Any
  ], TestSuccess] =
    test("depends on a shared layer 2") {
      assertCompletes
    }
  override def layer: ZLayer[Has[ZIOAppArgs], IOException, Has[Unit]] =
    SharedLayer.test

}

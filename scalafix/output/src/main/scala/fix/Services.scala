package fix

import zio.{Has, URIO, ZIO}
import zio._
import zio.test.Annotations
import zio.{ Console, Random, System }
import zio.Console.{ printLine, readLine }
import zio.test.{ Annotations, TestConfig }
import zio.test.environment.TestSystem

object Services {
  val random: URIO[Has[Random], Random] = ZIO.service[Random]
  val console: URIO[Has[Console], Console] = ZIO.service[Console]
  val live: ZIO[Has[System], Nothing, Unit] = ZIO.unit
  val live2: ZIO[Has[System], Nothing, Unit] = ZIO.unit
  val testConfig: URIO[Has[TestConfig], Unit] = ZIO.unit
  val testConfigService: URIO[Has[TestConfig], Unit] = ZIO.unit
  val testSystem: URIO[Has[TestSystem], Unit] = ZIO.unit
  val testSystemService: URIO[Has[TestSystem], Unit] = ZIO.unit
  val annotations: URIO[Has[Annotations], Unit] = ZIO.unit
  val annotationsService: URIO[Has[Annotations], Unit] = ZIO.unit

  val effect = readLine *> printLine("hi")
}
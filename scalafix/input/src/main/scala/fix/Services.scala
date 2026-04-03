/*
rule = Zio2Upgrade
*/
package fix

import zio.console.Console
import zio.{Has, URIO, ZIO}
import zio.random.Random
import zio.console.{getStrLn, putStrLn}
import zio._
import zio.test.{Annotations, Sized, TestConfig, TestLogger}
import zio.test.environment.{Live, TestConsole, TestRandom, TestSystem}

object Services {
  val random: URIO[Random, Random.Service] = ZIO.service[Random.Service]
  val console: URIO[Has[Console.Service], Console.Service] = ZIO.service[zio.console.Console.Service]
  val live: ZIO[zio.system.System, Nothing, Unit] = ZIO.unit
  val live2: ZIO[system.System, Nothing, Unit] = ZIO.unit
  val testConfig: URIO[TestConfig, Unit] = ZIO.unit
  val testConfigService: URIO[Has[TestConfig.Service], Unit] = ZIO.unit
  val testSystem: URIO[TestSystem, Unit] = ZIO.unit
  val testSystemService: URIO[Has[TestSystem.Service], Unit] = ZIO.unit
  val annotations: URIO[Annotations, Unit] = ZIO.unit
  val annotationsService: URIO[Has[Annotations.Service], Unit] = ZIO.unit
  val testLogger: URIO[TestLogger, Unit] = ZIO.unit
  val testLoggerService: URIO[Has[TestLogger.Service], Unit] = ZIO.unit
  val sized: URIO[Sized, Unit] = ZIO.unit
  val sizedService: URIO[Has[Sized.Service], Unit] = ZIO.unit
  val testConsole: URIO[TestConsole, Unit] = ZIO.unit
  val testConsoleService: URIO[Has[TestConsole.Service], Unit] = ZIO.unit
  val testRandom: URIO[TestRandom, Unit] = ZIO.unit
  val testRandomService: URIO[Has[TestRandom.Service], Unit] = ZIO.unit
  val testLive: URIO[Live, Unit] = ZIO.unit
  val testLiveService: URIO[Has[Live.Service], Unit] = ZIO.unit

  val effect = getStrLn *> putStrLn("hi")
}
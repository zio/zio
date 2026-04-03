package fix

import zio.{URIO, ZIO}
import zio.{ Console, Random, System }
import zio.test.{ Annotations, Live, Sized, TestConfig, TestConsole, TestLogger, TestRandom, TestSystem }

object ServicesIntermediateMilestone {
  val randomManual: URIO[Random, Random] = ZIO.service[Random]
  
  val random: URIO[Random, Random] = ZIO.service[Random]
  val console: URIO[Console, Console] = ZIO.service[Console]
  val live: ZIO[System, Nothing, Unit] = ZIO.unit
  val live2: ZIO[System, Nothing, Unit] = ZIO.unit
  val testConfig: URIO[TestConfig, Unit] = ZIO.unit
  val testConfigService: URIO[TestConfig, Unit] = ZIO.unit
  val testSystem: URIO[TestSystem, Unit] = ZIO.unit
  val testSystemService: URIO[TestSystem, Unit] = ZIO.unit
  val annotations: URIO[Annotations, Unit] = ZIO.unit
  val annotationsService: URIO[Annotations, Unit] = ZIO.unit
  val testLogger: URIO[TestLogger, Unit] = ZIO.unit
  val testLoggerService: URIO[TestLogger, Unit] = ZIO.unit
  val sized: URIO[Sized, Unit] = ZIO.unit
  val sizedService: URIO[Sized, Unit] = ZIO.unit
  val testConsole: URIO[TestConsole, Unit] = ZIO.unit
  val testConsoleService: URIO[TestConsole, Unit] = ZIO.unit
  val testRandom: URIO[TestRandom, Unit] = ZIO.unit
  val testRandomService: URIO[TestRandom, Unit] = ZIO.unit
  val testLive: URIO[Live, Unit] = ZIO.unit
  val testLiveService: URIO[Live, Unit] = ZIO.unit
}
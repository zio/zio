/*
rule = Zio2Upgrade
*/
package fix

import zio.console.Console
import zio.{Has, URIO, ZIO}
import zio.random.Random
import zio.console.{getStrLn, putStrLn}
import zio._
import zio.test.TestConfig
import zio.test.environment.TestSystem

object Services {
  val random: URIO[Random, Random.Service] = ZIO.service[Random.Service]
  val console: URIO[Has[Console.Service], Console.Service] = ZIO.service[zio.console.Console.Service]
  val live: ZIO[zio.system.System, Nothing, Unit] = ZIO.unit
  val live2: ZIO[system.System, Nothing, Unit] = ZIO.unit
  val testConfig: URIO[TestConfig, Unit] = ZIO.unit
  val testSystem: URIO[TestSystem, Unit] = ZIO.unit

  val effect = getStrLn *> putStrLn("hi")
}
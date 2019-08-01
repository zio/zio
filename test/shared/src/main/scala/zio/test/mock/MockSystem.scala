package zio.test.mock

import zio.{ Ref, UIO, IO, ZIO }
import zio.system.System

trait MockSystem extends System {
  val system: MockSystem.Service
}

object MockSystem {

  trait Service extends System.Service {
    def putEnv(name: String, value: String): UIO[Unit]
    def putProperty(name: String, value: String): UIO[Unit]
    def setLineSeparator(lineSep: String): UIO[Unit]
    def clearEnv(variable: String): UIO[Unit]
    def clearProperty(prop: String): UIO[Unit]
  }

  case class Mock(systemState: Ref[MockSystem.Data]) extends MockSystem.Service {

    override def env(variable: String): IO[SecurityException, Option[String]] =
      systemState.get.map(_.envs.get(variable))

    override def property(prop: String): IO[Throwable, Option[String]] =
      systemState.get.map(_.properties.get(prop))

    override val lineSeparator: IO[Nothing, String] =
      systemState.get.map(_.lineSeparator)

    def putEnv(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs.updated(name, value))).unit

    def putProperty(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties.updated(name, value))).unit

    def setLineSeparator(lineSep: String): UIO[Unit] =
      systemState.update(_.copy(lineSeparator = lineSep)).unit

    def clearEnv(variable: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs - variable)).unit

    def clearProperty(prop: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties - prop)).unit
  }

  def make(data: Data): UIO[MockSystem] =
    makeMock(data).map { mock =>
      new MockSystem {
        val system = mock
      }
    }

  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  def putEnv(name: String, value: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.putEnv(name, value))

  def putProperty(name: String, value: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.putProperty(name, value))

  def setLineSeparator(lineSep: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.setLineSeparator(lineSep))

  def clearEnv(variable: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.clearEnv(variable))

  def clearProperty(prop: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.clearProperty(prop))

  val DefaultData: Data = Data(Map(), Map(), "\n")

  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}

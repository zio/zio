package zio.test.mock

import zio.{ Ref, UIO, ZIO }
import zio.system.System

case class MockSystem(systemState: Ref[MockSystem.Data]) extends System.Service[Any] {

  override def env(variable: String): ZIO[Any, SecurityException, Option[String]] =
    systemState.get.map(_.envs.get(variable))

  override def property(prop: String): ZIO[Any, Throwable, Option[String]] =
    systemState.get.map(_.properties.get(prop))

  override val lineSeparator: ZIO[Any, Nothing, String] =
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

object MockSystem {
  val DefaultData: Data = Data(Map(), Map(), "\n")

  def make(data: Data): UIO[MockSystem] =
    Ref.make(data).map(MockSystem(_))

  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )

  def putEnv(name: String, value: String): ZIO[MockEnvironment, Nothing, Unit] =
    ZIO.accessM(_.system.putEnv(name, value))

  def putProperty(name: String, value: String): ZIO[MockEnvironment, Nothing, Unit] =
    ZIO.accessM(_.system.putProperty(name, value))

  def setLineSeparator(lineSep: String): ZIO[MockEnvironment, Nothing, Unit] =
    ZIO.accessM(_.system.setLineSeparator(lineSep))

  def clearEnv(variable: String): ZIO[MockEnvironment, Nothing, Unit] =
    ZIO.accessM(_.system.clearEnv(variable))

  def clearProperty(prop: String): ZIO[MockEnvironment, Nothing, Unit] =
    ZIO.accessM(_.system.clearProperty(prop))
}

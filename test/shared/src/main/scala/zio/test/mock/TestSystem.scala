package zio.test.mock

import zio.{ Ref, UIO, ZIO }
import zio.system.System

case class TestSystem(private val ref: Ref[TestSystem.Data]) extends System.Service[Any] {

  override def env(variable: String): ZIO[Any, SecurityException, Option[String]] =
    ref.get.map(_.envs.get(variable))

  override def property(prop: String): ZIO[Any, Throwable, Option[String]] =
    ref.get.map(_.properties.get(prop))

  override val lineSeparator: ZIO[Any, Nothing, String] =
    ref.get.map(_.lineSeparator)

  def putEnv(name: String, value: String): UIO[Unit] =
    ref.update(data => data.copy(envs = data.envs.updated(name, value))).unit

  def putProperty(name: String, value: String): UIO[Unit] =
    ref.update(data => data.copy(properties = data.properties.updated(name, value))).unit

  def setLineSeperator(lineSep: String): UIO[Unit] =
    ref.update(_.copy(lineSeparator = lineSep)).unit
}

object TestSystem {
  val DefaultData: Data = Data(Map(), Map(), "\n")

  def apply(data: Data): UIO[TestSystem] =
    Ref.make(data).map(TestSystem(_))

  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}

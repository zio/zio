package scalaz.zio.testkit

import scalaz.zio.{ Ref, UIO, ZIO }
import scalaz.zio.system.System

case class TestSystem(ref: Ref[TestSystem.Data]) extends System.Service[Any] {

  override def env(variable: String): ZIO[Any, SecurityException, Option[String]] =
    ref.get.map(_.envs.get(variable))

  override def property(prop: String): ZIO[Any, Throwable, Option[String]] =
    ref.get.map(_.properties.get(prop))

  override val lineSeparator: ZIO[Any, Nothing, String] =
    ref.get.map(_.lineSeparator)
}

object TestSystem {

  def apply(data: Data): UIO[TestSystem] =
    Ref.make(data).map(TestSystem(_))

  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}

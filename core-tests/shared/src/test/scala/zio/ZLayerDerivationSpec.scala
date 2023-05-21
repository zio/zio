package zio

import zio.test._

object ZLayerDerivationSpec extends ZIOBaseSpec {
  case class ZeroDependency()
  case class OneDependency(d1: String)
  case class TwoDependencies(d1: String, d2: Int)

  val derivedZero = ZLayer.derive[ZeroDependency]
  val derivedOne  = ZLayer.derive[OneDependency]
  val derivedTwo  = ZLayer.derive[TwoDependencies]

  override def spec = suite("ZLayer.derive[A]")(
    test("Zero dependency") {
      for {
        d0 <- ZIO.service[ZeroDependency]
      } yield assertTrue(d0 == ZeroDependency())
    },
    test("One dependency") {
      for {
        d1 <- ZIO.service[OneDependency]
      } yield assertTrue(d1 == OneDependency("one"))
    },
    test("Two dependencies") {
      for {
        d1 <- ZIO.service[TwoDependencies]
      } yield assertTrue(d1 == TwoDependencies("one", 2))
    }
  ).provide(
    derivedZero,
    derivedOne,
    derivedTwo,
    ZLayer.succeed("one"),
    ZLayer.succeed(2)
  )
}

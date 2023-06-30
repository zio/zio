package zio

import zio.test._

object ZLayerDerivationSpec extends ZIOBaseSpec {
  case class ZeroDependencies()
  case class OneDependency(d1: String)
  case class TwoDependencies(d1: String, d2: Int)

  val derivedZero = ZLayer.derive[ZeroDependencies]
  val derivedOne = ZLayer.derive[OneDependency]
  val derivedTwo = ZLayer.derive[TwoDependencies]
  override def spec = suite("ZLayerDerivationSpec")(
    test("ZLayer.derive[ZeroDependencies]") {
      for {
        d1 <- ZIO.service[ZeroDependencies]
      } yield assertTrue(d1 == ZeroDependencies())
    },
    test("ZLayer.derive[OneDependency]") {
      for {
        d1 <- ZIO.service[OneDependency]
      } yield assertTrue(d1 == OneDependency("one"))
    },
    test("ZLayer.derive[TwoDependencies]") {
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
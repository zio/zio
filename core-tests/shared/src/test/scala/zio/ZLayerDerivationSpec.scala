package zio

import zio._
import zio.test._

object ZLayerDerivationSpec extends ZIOBaseSpec {
  case class ZeroDependency()
  case class OneDependency(d1: String)
  case class TwoDependencies(d1: String, d2: Int)

  val derivedZero = ZLayer.derive[ZeroDependency]
  val derivedOne  = ZLayer.derive[OneDependency]
  val derivedTwo  = ZLayer.derive[TwoDependencies]

  case class ZeroDependencyWithPromise(p1: Promise[Nothing, Int])
  case class OneDependencyWithPromise(d1: String, p1: Promise[Nothing, Int])
  case class OneDependencyWithQueue(d1: String, q1: Queue[Int])
  case class OneDependencyWithHub(d1: String, q1: Hub[Int])

  val derivedZeroWithPromise = ZLayer.derive[ZeroDependencyWithPromise]
  val derivedOneWithPromise  = ZLayer.derive[OneDependencyWithPromise]
  val derivedOneWithQueue    = ZLayer.derive[OneDependencyWithQueue]
  val derivedOneWithHub      = ZLayer.derive[OneDependencyWithHub]

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
    },
    test("Zero dependency with Promise") {
      for {
        svc    <- ZIO.service[ZeroDependencyWithPromise]
        isDone <- svc.p1.isDone
      } yield assertTrue(!isDone)
    },
    test("One dependency with Promise") {
      for {
        svc    <- ZIO.service[OneDependencyWithPromise]
        isDone <- svc.p1.isDone
      } yield assertTrue(svc.d1 == "one", !isDone)
    },
    test("One dependency with Queue") {
      for {
        svc       <- ZIO.service[OneDependencyWithQueue]
        queueSize <- svc.q1.size
      } yield assertTrue(svc.d1 == "one", queueSize == 0)
    },
    test("One dependency with Hub") {
      for {
        svc     <- ZIO.service[OneDependencyWithHub]
        hubSize <- svc.q1.size
      } yield assertTrue(svc.d1 == "one", hubSize == 0)
    }
  ).provide(
    derivedZero,
    derivedOne,
    derivedTwo,
    derivedZeroWithPromise,
    derivedOneWithPromise,
    derivedOneWithQueue,
    derivedOneWithHub,
    ZLayer.succeed("one"),
    ZLayer.succeed(2)
  )
}

package zio

import zio.test.Assertion._
import zio.test._
import zio.test.environment._

//import zio.test.TestAspect._

object ZLayerSpec extends ZIOBaseSpec {
  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def testSize[R <: Has[_]](layer: ZLayer.NoDeps[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    layer.build.use { env =>
      ZIO.succeed(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label))
    }

  def spec = suite("ZLayerSpec")(
    zio.test.testM("Size of >>> (1)") {
      val layer = ZLayer.succeed(1) >>> ZLayer.fromService((i: Int) => Has(i.toString))

      testSize(layer, 1)
    },
    zio.test.testM("Size of >>> (2)") {
      val layer = ZLayer.succeed(1) >>>
        (ZLayer.fromService((i: Int) => Has(i.toString)) ++
          ZLayer.fromService((i: Int) => Has(i % 2 == 0)))

      testSize(layer, 2)
    },
    zio.test.testM("Size of Test layers") {
      for {
        r1 <- testSize(Annotations.live, 1, "Annotations.live")
        r2 <- testSize(TestConsole.default, 2, "TestConsole.default")
        r3 <- testSize(ZEnv.live >>> Live.default, 1, "Live.default")
        r4 <- testSize(ZEnv.live >>> TestRandom.deterministic, 2, "TestRandom.live")
        r5 <- testSize(Sized.live(100), 1, "Sized.live(100)")
        r6 <- testSize(TestSystem.default, 2, "TestSystem.default")
      } yield r1 && r2 && r3 && r4 && r5 && r6
    },
    zio.test.testM("Size of >>> (9)") {
      val layer = (ZEnv.live >>>
        (Annotations.live ++ TestConsole.default ++ Live.default ++ TestRandom.deterministic ++ Sized
          .live(100) ++ TestSystem.default))

      testSize(layer, 9)
    }
  )
}

package zio

import zio.test.Assertion._
import zio.test._
import zio.test.environment._

object ZLayerSpec extends ZIOBaseSpec {
  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def testSize[R <: Has[_]](layer: ZLayer.NoDeps[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    layer.build.use { env =>
      ZIO.succeedNow(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label))
    }

  type Module1 = Has[Module1.Service]

  object Module1 {
    trait Service
  }

  def makeLayer1(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module1] =
    ZLayer {
      ZManaged.make(ref.update(_ :+ "Acquiring Module 1").as(Has(new Module1.Service {})))(_ =>
        ref.update(_ :+ "Releasing Module 1")
      )
    }

  type Module2 = Has[Module2.Service]

  object Module2 {
    trait Service
  }

  def makeLayer2(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module2] =
    ZLayer {
      ZManaged.make(ref.update(_ :+ "Acquiring Module 2").as(Has(new Module2.Service {})))(_ =>
        ref.update(_ :+ "Releasing Module 2")
      )
    }

  type Module3 = Has[Module3.Service]

  object Module3 {
    trait Service
  }

  def makeLayer3(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module3] =
    ZLayer {
      ZManaged.make(ref.update(_ :+ "Acquiring Module 3").as(Has(new Module3.Service {})))(_ =>
        ref.update(_ :+ "Releasing Module 3")
      )
    }

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec = suite("ZLayerSpec")(
    testM("Size of >>> (1)") {
      val layer = ZLayer.succeed(1) >>> ZLayer.fromService((i: Int) => Has(i.toString))

      testSize(layer, 1)
    },
    testM("Size of >>> (2)") {
      val layer = ZLayer.succeed(1) >>>
        (ZLayer.fromService((i: Int) => Has(i.toString)) ++
          ZLayer.fromService((i: Int) => Has(i % 2 == 0)))

      testSize(layer, 2)
    },
    testM("Size of Test layers") {
      for {
        r1 <- testSize(Annotations.live, 1, "Annotations.live")
        r2 <- testSize(TestConsole.default, 2, "TestConsole.default")
        r3 <- testSize(ZEnv.live >>> Live.default, 1, "Live.default")
        r4 <- testSize(ZEnv.live >>> TestRandom.deterministic, 2, "TestRandom.live")
        r5 <- testSize(Sized.live(100), 1, "Sized.live(100)")
        r6 <- testSize(TestSystem.default, 2, "TestSystem.default")
      } yield r1 && r2 && r3 && r4 && r5 && r6
    },
    testM("Size of >>> (9)") {
      val layer = (ZEnv.live >>>
        (Annotations.live ++ TestConsole.default ++ Live.default ++ TestRandom.deterministic ++ Sized
          .live(100) ++ TestSystem.default))

      testSize(layer, 9)
    },
    testM("sharing with ++") {
      val expected = Vector(
        "Acquiring Module 1",
        "Releasing Module 1"
      )
      for {
        ref    <- makeRef
        layer1 = makeLayer1(ref)
        env    = (layer1 ++ layer1).build
        _      <- env.use_(ZIO.unit)
        actual <- ref.get
      } yield assert(actual)(equalTo(expected))
    },
    testM("sharing with >>>") {
      val expected = Vector(
        "Acquiring Module 1",
        "Releasing Module 1"
      )
      for {
        ref    <- makeRef
        layer1 = makeLayer1(ref)
        env    = (layer1 >>> layer1).build
        _      <- env.use_(ZIO.unit)
        actual <- ref.get
      } yield assert(actual)(equalTo(expected))
    },
    testM("sharing with multiple layers") {
      val expected = Vector(
        "Acquiring Module 1",
        "Acquiring Module 2",
        "Acquiring Module 3",
        "Releasing Module 3",
        "Releasing Module 2",
        "Releasing Module 1"
      )
      for {
        ref    <- makeRef
        layer1 = makeLayer1(ref)
        layer2 = makeLayer2(ref)
        layer3 = makeLayer3(ref)
        env    = ((layer1 >>> layer2) ++ (layer1 >>> layer3)).build
        _      <- env.use_(ZIO.unit)
        actual <- ref.get
      } yield assert(actual)(equalTo(expected))
    },
    testM("finalizers with ++") {
      val expected = Vector(
        "Acquiring Module 1",
        "Acquiring Module 2",
        "Releasing Module 2",
        "Releasing Module 1"
      )
      for {
        ref    <- makeRef
        layer1 = makeLayer1(ref)
        layer2 = makeLayer2(ref)
        env    = (layer1 ++ layer2).build
        _      <- env.use_(ZIO.unit)
        actual <- ref.get
      } yield assert(actual)(equalTo(expected))
    },
    testM("finalizers with >>>") {
      val expected = Vector(
        "Acquiring Module 1",
        "Acquiring Module 2",
        "Releasing Module 2",
        "Releasing Module 1"
      )
      for {
        ref    <- makeRef
        layer1 = makeLayer1(ref)
        layer2 = makeLayer2(ref)
        env    = (layer1 >>> layer2).build
        _      <- env.use_(ZIO.unit)
        actual <- ref.get
      } yield assert(actual)(equalTo(expected))
    },
    testM("finalizers with multiple layers") {
      val expected = Vector(
        "Acquiring Module 1",
        "Acquiring Module 2",
        "Acquiring Module 3",
        "Releasing Module 3",
        "Releasing Module 2",
        "Releasing Module 1"
      )
      for {
        ref    <- makeRef
        layer1 = makeLayer1(ref)
        layer2 = makeLayer2(ref)
        layer3 = makeLayer3(ref)
        env    = (layer1 >>> layer2 >>> layer3).build
        _      <- env.use_(ZIO.unit)
        actual <- ref.get
      } yield assert(actual)(equalTo(expected))
    }
  )
}

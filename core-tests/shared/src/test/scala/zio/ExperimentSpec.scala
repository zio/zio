import zio.test.Assertion._
import zio.test._
import zio.{ ZLayer => _, _ }

object ExperimentSpec extends DefaultRunnableSpec {
  import Experiment._

  type Module1 = Has[Module1.Service]

  object Module1 {
    trait Service
  }

  def makeLayer1(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module1] =
    ZLayer.fromManaged(
      ZManaged.make(ref.update(_ :+ "Acquiring Module 1").as(Has(new Module1.Service {})))(_ =>
        ref.update(_ :+ "Releasing Module 1")
      )
    )

  type Module2 = Has[Module2.Service]

  object Module2 {
    trait Service
  }

  def makeLayer2(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module2] =
    ZLayer.fromManaged(
      ZManaged.make(ref.update(_ :+ "Acquiring Module 2").as(Has(new Module2.Service {})))(_ =>
        ref.update(_ :+ "Releasing Module 2")
      )
    )

  type Module3 = Has[Module3.Service]

  object Module3 {
    trait Service
  }

  def makeLayer3(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module3] =
    ZLayer.fromManaged(
      ZManaged.make(ref.update(_ :+ "Acquiring Module 3").as(Has(new Module3.Service {})))(_ =>
        ref.update(_ :+ "Releasing Module 3")
      )
    )

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec = suite("ExperimentSpec")(
    testM("layers are shared with ++") {
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
    testM("layers are shared with >>>") {
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
    testM("multiple layers acquired and released in correct order with ++") {
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
    testM("multiple layers acquired and released in correct order with >>>") {
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
    testM("shared dependency at different levels") {
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
    testM("three layers") {
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

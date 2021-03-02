package zio.test

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test.AssertionM.Render.param

object AutoLayerSpec extends ZIOBaseSpec {
  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec: ZSpec[Environment, Failure] =
    suite("AutoLayerSpec")(
      suite("provideLayer")(
        suite("meta-suite") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
          testM("automatically constructs a layer from its dependencies") {
            val program = ZIO.service[Int]
            assertM(program)(equalTo(128))
          }.provideLayer(doubleLayer, stringLayer, intLayer)
        },
        testM("reports missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program
          val checked =
            typeCheck("""testM("foo")(assertM(program)(anything)).provideLayer(ZLayer.succeed(3))""")
          assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
        } @@ TestAspect.exceptDotty,
        testM("reports multiple missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program

          val checked = typeCheck("""testM("foo")(assertM(program)(anything)).provideLayer()""")
          assertM(checked)(
            isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
          )
        } @@ TestAspect.exceptDotty,
        testM("reports missing transitive dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked = typeCheck("""testM("foo")(assertM(program)(anything)).provideLayer(OldLady.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoLayerSpec.TestLayers.Fly") &&
                containsStringWithoutAnsi("for TestLayers.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        testM("reports nested missing transitive dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked =
            typeCheck("""testM("foo")(assertM(program)(anything)).provideLayer(OldLady.live, Fly.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoLayerSpec.TestLayers.Spider") &&
                containsStringWithoutAnsi("for TestLayers.Fly.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        testM("reports circular dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked =
            typeCheck(
              """testM("foo")(assertM(program)(anything)).provideLayer(OldLady.live, Fly.manEatingFly)"""
            )
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("TestLayers.Fly.manEatingFly") &&
                containsStringWithoutAnsi("both requires and is transitively required by TestLayers.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty
      )
    )

  object TestLayers {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URLayer[Has[Fly], Has[OldLady]] = ZLayer.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URLayer[Has[Spider], Has[Fly]]          = ZLayer.succeed(new Fly {})
      def manEatingFly: URLayer[Has[OldLady], Has[Fly]] = ZLayer.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: ULayer[Has[Spider]] = ZLayer.succeed(new Spider {})
    }
  }
}

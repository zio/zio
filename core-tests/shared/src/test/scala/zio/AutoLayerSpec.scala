package zio

import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test.AssertionM.Render.param
import zio.test._
import zio.test.environment.TestConsole

object AutoLayerSpec extends ZIOBaseSpec {

  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec: ZSpec[Environment, Failure] =
    suite("AutoLayerSpec")(
      suite("`zio.provideLayerAuto`")(
        testM("automatically constructs a layer from its dependencies") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer

          val program  = ZIO.service[Int]
          val provided = program.provideLayerAuto(intLayer, stringLayer, doubleLayer)
          assertM(provided)(equalTo(128))
        },
        testM("automatically memoizes non-val layers") {
          def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, Has[String]] =
            ref.update(_ + 1).as("Howdy").toLayer

          val layerA: URLayer[Has[String], Has[Int]]     = ZLayer.succeed(1)
          val layerB: URLayer[Has[String], Has[Boolean]] = ZLayer.succeed(true)

          for {
            ref    <- Ref.make(0)
            _      <- ZIO.services[Int, Boolean].provideLayerAuto(layerA, layerB, sideEffectingLayer(ref))
            result <- ref.get
          } yield assert(result)(equalTo(1))
        },
        testM("reports missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program

          val checked = typeCheck("program.provideLayerAuto(ZLayer.succeed(3))")
          assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
        } @@ TestAspect.exceptDotty,
        testM("reports multiple missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program

          val checked = typeCheck("program.provideLayerAuto()")
          assertM(checked)(
            isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
          )
        } @@ TestAspect.exceptDotty,
        testM("reports missing transitive dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked = typeCheck("program.provideLayerAuto(OldLady.live)")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.AutoLayerSpec.TestLayers.Fly") &&
                containsStringWithoutAnsi("for TestLayers.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        testM("reports nested missing transitive dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked = typeCheck("program.provideLayerAuto(OldLady.live, Fly.live)")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.AutoLayerSpec.TestLayers.Spider") &&
                containsStringWithoutAnsi("for TestLayers.Fly.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        testM("reports circular dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked = typeCheck("program.provideLayerAuto(OldLady.live, Fly.manEatingFly)")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("TestLayers.Fly.manEatingFly") &&
                containsStringWithoutAnsi("both requires and is transitively required by TestLayers.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty
      ),
      suite("provideCustomLayerAuto")(
        testM("automatically constructs a layer from its dependencies, leaving off ZEnv") {
          val stringLayer = console.getStrLn.orDie.toLayer
          val program     = ZIO.service[String].zipWith(random.nextInt)((str, int) => s"$str $int")
          val provided = TestConsole.feedLines("Your Lucky Number is:") *>
            program.provideCustomLayerAuto(stringLayer)

          assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
        }
      ),
      suite("`ZLayer.fromAuto`")(
        testM("automatically constructs a layer from its dependencies") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer

          val layer    = ZLayer.fromAuto[Has[Int]](intLayer, stringLayer, doubleLayer)
          val provided = ZIO.service[Int].provideLayer(layer)
          assertM(provided)(equalTo(128))
        }
      ),
      suite("`ZLayer.fromSomeAuto`")(
        testM("automatically constructs a layer from its dependencies, leaving off some remainder") {
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
          val program     = ZIO.service[Int]

          val layer    = ZLayer.fromSomeAuto[Has[Double] with Has[Boolean], Has[Int]](intLayer, stringLayer)
          val provided = program.provideLayer(ZLayer.succeed(true) ++ ZLayer.succeed(100.1) >>> layer)
          assertM(provided)(equalTo(128))
        }
      ),
      suite("`ZLayer.fromAutoDebug`")(
        testM("automatically constructs and visualizes a layer from its dependencies") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
          val _           = ZLayer.fromAuto[Has[Int]](intLayer, stringLayer, doubleLayer)

          val checked = typeCheck("ZLayer.fromAutoDebug[Has[Int]](intLayer, stringLayer, doubleLayer)")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("""
         intLayer         
      ┌──────┴─────┐      
 stringLayer  doubleLayer""".trim)
            )
          )
        }
      ) @@ TestAspect.exceptDotty
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

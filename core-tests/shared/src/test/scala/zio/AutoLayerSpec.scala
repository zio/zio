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
        testM("reports missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program

          val checked = typeCheck("program.provideLayerAuto(ZLayer.succeed(3))")
          assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
        },
        testM("reports multiple missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program

          val checked = typeCheck("program.provideLayerAuto()")
          assertM(checked)(
            isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
          )
        },
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
        },
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
        },
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
        }
      ),
      suite("provideCustomLayerAuto")(
        testM("automatically constructs a layer from its dependencies, leaving off ZEnv") {
          val stringLayer = console.getStrLn.orDie.toLayer
          val program     = ZIO.service[String].zipWith(random.nextInt)((str, int) => s"$str $int")
          val provided    = TestConsole.feedLines("Your Lucky Number is:") *> program.provideCustomLayerAuto(stringLayer)

          assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
        }
      ),
      suite("`ZLayer.fromAuto`")(
        testM("automatically constructs a layer from its dependencies") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
          val program     = ZIO.service[Int]

          val layer    = ZLayer.fromAuto[Has[Int]](intLayer, stringLayer, doubleLayer)
          val provided = program.provideLayer(layer)
          assertM(provided)(equalTo(128))
        }
      ),
      suite("`ZLayer.fromAutoDebug`")(
        testM("automatically constructs and visualizes a layer from its dependencies") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
          val program     = ZIO.service[Int]
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

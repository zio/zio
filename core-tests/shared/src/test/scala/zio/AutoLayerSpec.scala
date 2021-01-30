package zio

import zio.blocking.Blocking
import zio.console.Console
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestConsole

object AutoLayerSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("AutoLayerSpec")(
      suite("provideLayerAuto")(
        testM("automatically constructs a layer from its dependencies") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
          val program     = ZIO.service[Int]
          val provided    = program.provideLayerAuto(intLayer, stringLayer, doubleLayer)
          assertM(provided)(equalTo(128))
        },
        testM("reports missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")

          val checked = typeCheck("val provided = program.provideLayerAuto(ZLayer.succeed(3))")
          assertM(checked)(isLeft(containsString("missing String")))
        },
        testM("reports multiple missing top-level layers") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")

          val checked = typeCheck("val provided = program.provideLayerAuto()")
          assertM(checked)(isLeft(containsString("missing String") && containsString("missing Int")))
        },
        testM("reports missing transitive dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)

          val checked = typeCheck("val provided = program.provideLayerAuto(OldLady.live)")
          assertM(checked)(
            isLeft(
              containsString("provide zio.AutoLayerSpec.TestLayers.Fly") &&
                containsString("for TestLayers.OldLady.live")
            )
          )
        },
        testM("reports nested missing transitive dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)

          val checked = typeCheck("val provided = program.provideLayerAuto(OldLady.live, Fly.live)")
          assertM(checked)(
            isLeft(
              containsString("provide zio.AutoLayerSpec.TestLayers.Spider") &&
                containsString("for TestLayers.Fly.live")
            )
          )
        },
        testM("reports circular dependencies") {
          import TestLayers._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)

          val checked = typeCheck("val provided = program.provideLayerAuto(OldLady.live, Fly.manEatingFly)")
          assertM(checked)(
            isLeft(
              containsString("TestLayers.Fly.manEatingFly") &&
                containsString("both requires and is transitively required by TestLayers.OldLady.live")
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

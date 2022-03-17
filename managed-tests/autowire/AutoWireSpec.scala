package zio.managed.autowire

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion.{equalTo, isLeft}
import zio.test.AssertionM.Render.param
import zio.test._

object AutoWireSpec extends ZIOBaseSpec {

  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec =
    suite("AutoWireSpec")(
      suite("ZManaged")(
        suite("`zmanaged.provide`")(
          test("automatically constructs a layer") {
            val doubleLayer = ZLayer.succeed(100.1)
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer =
              ZLayer {
                for {
                  str    <- ZManaged.service[String]
                  double <- ZManaged.service[Double]
                } yield str.length + double.toInt
              }

            val program  = ZManaged.service[Int]
            val provided = program.provide(intLayer, stringLayer, doubleLayer)
            assertM(provided.useNow)(equalTo(128))
          },
          test("automatically memoizes non-val layers") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, String] =
              ref.update(_ + 1).as("Howdy").toLayer

            val layerA: URLayer[String, Int]     = ZLayer.succeed(1)
            val layerB: URLayer[String, Boolean] = ZLayer.succeed(true)

            (for {
              ref <- Ref.make(0).toManaged
              _ <- (ZManaged.service[Int] <*> ZManaged.service[Boolean])
                     .provide(layerA, layerB, sideEffectingLayer(ref))
              result <- ref.get.toManaged
            } yield assert(result)(equalTo(1))).useNow
          },
          test("reports missing top-level layers") {
            val program: ZManaged[String with Int, Nothing, String] = ZManaged.succeed("test")
            val _                                                   = program

            val checked = typeCheck("program.provide(ZLayer.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("String")))
          } @@ TestAspect.exceptScala3,
          test("reports multiple missing top-level layers") {
            val program: ZManaged[String with Int, Nothing, String] = ZManaged.succeed("test")
            val _                                                   = program

            val checked = typeCheck("program.provide()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("String") && containsStringWithoutAnsi("Int"))
            )
          } @@ TestAspect.exceptScala3,
          test("reports missing transitive dependencies") {
            import TestLayer._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.provide(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("zio.autowire.AutoWireSpec.TestLayer.Fly") &&
                  containsStringWithoutAnsi("Required by TestLayer.OldLady.live")
              )
            )
          } @@ TestAspect.exceptScala3,
          test("reports nested missing transitive dependencies") {
            import TestLayer._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.provide(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("zio.autowire.AutoWireSpec.TestLayer.Spider") &&
                  containsStringWithoutAnsi("Required by TestLayer.Fly.live")
              )
            )
          } @@ TestAspect.exceptScala3,
          test("reports circular dependencies") {
            import TestLayer._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.provide(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestLayer.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("OldLady.live") &&
                  containsStringWithoutAnsi(
                    "A layer simultaneously requires and is required by another"
                  )
              )
            )
          } @@ TestAspect.exceptScala3
        ),
        suite("provideCustom")(
          test("automatically constructs a layer, leaving off ZEnv") {
            val stringLayer = Console.readLine.orDie.toLayer
            val program     = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.provideCustom(stringLayer)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          },
          test("gives precedence to provided layers") {
            {
              Console.printLine("Hello") *>
                Random.nextInt.map { i =>
                  assertTrue(i == 1094383425)
                }
            }.provideCustom(TestRandom.make(TestRandom.Data(10, 10)))
          }
        ),
        suite("provideSome")(
          test("automatically constructs a layer, leaving off some environment") {
            val stringLayer = Console.readLine.orDie.toLayer
            val program     = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.provideSome[Random with Console](stringLayer)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          },
          test("gives precedence to provided layers") {
            {
              Console.printLine("Hello") *>
                Random.nextInt.map { i =>
                  assertTrue(i == 1094383425)
                }
            }.provideSome[Console](TestRandom.make(TestRandom.Data(10, 10)))
          }
        ),
        suite(".unit")(
          test("run layers for their side effects and suppresses unused layer warnings") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, String] =
              ref.update(_ + 1).as("Howdy").toLayer

            for {
              ref <- Ref.make(0)
              _ <- ZIO
                     .service[Int]
                     .provide(
                       sideEffectingLayer(ref).unit,
                       ZLayer.succeed(12)
                     )
              result <- ref.get
            } yield assertTrue(result == 1)

          }
        )
      )
    )

  object TestLayer {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URLayer[Fly, OldLady] = ZLayer.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URLayer[Spider, Fly]          = ZLayer.succeed(new Fly {})
      def manEatingFly: URLayer[OldLady, Fly] = ZLayer.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: ULayer[Spider] = ZLayer.succeed(new Spider {})
    }
  }
}

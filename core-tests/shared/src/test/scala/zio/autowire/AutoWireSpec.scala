package zio.autowire

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
      suite("ZIO")(
        suite("`zio.inject`")(
          test("automatically constructs a layer from its dependencies") {
            val doubleLayer: ULayer[Has[Double]] = ZLayer.succeed(100.1)
            val stringLayer                      = ZLayer.succeed("this string is 28 chars long")
            val intLayer =
              (for {
                str    <- ZIO.service[String]
                double <- ZIO.service[Double]
              } yield str.length + double.toInt).toLayer

            val program: URIO[Has[Int], Int]     = ZIO.service[Int]
            val injected: ZIO[Any, Nothing, Int] = program.inject(intLayer, stringLayer, doubleLayer)

            injected.map(result => assertTrue(result == 128))
          },
          test("automatically memoizes non-val layers") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, Has[String]] =
              ref.update(_ + 1).as("Howdy").toLayer

            val layerA: URLayer[Has[String], Has[Int]]     = ZLayer.succeed(1)
            val layerB: URLayer[Has[String], Has[Boolean]] = ZLayer.succeed(true)

            for {
              ref    <- Ref.make(0)
              _      <- (ZIO.service[Int] <*> ZIO.service[Boolean]).inject(layerA, layerB, sideEffectingLayer(ref))
              result <- ref.get
            } yield assertTrue(result == 1)
          },
          test("reports duplicate layers") {
            val checked =
              typeCheck("ZIO.service[Int].inject(ZLayer.succeed(12), ZLayer.succeed(13))")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Int is provided by multiple layers") &&
                  containsStringWithoutAnsi("ZLayer.succeed(12)") &&
                  containsStringWithoutAnsi("ZLayer.succeed(13)")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports unused, extra layers") {
            val someLayer: URLayer[Has[Double], Has[String]] = ZLayer.succeed("hello")
            val doubleLayer: ULayer[Has[Double]]             = ZLayer.succeed(1.0)
            val _                                            = (someLayer, doubleLayer)

            val checked =
              typeCheck("ZIO.service[Int].inject(ZLayer.succeed(12), doubleLayer, someLayer)")
            assertM(checked)(isLeft(containsStringWithoutAnsi("unused")))
          } @@ TestAspect.exceptDotty,
          test("reports missing top-level layers") {
            val program: URIO[Has[String] with Has[Int], String] = UIO("test")
            val _                                                = program

            val checked = typeCheck("program.inject(ZLayer.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level layers") {
            val program: URIO[Has[String] with Has[Int], String] = UIO("test")
            val _                                                = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestLayers._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestLayers.Fly") &&
                  containsStringWithoutAnsi("for TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestLayers._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestLayers.Spider") &&
                  containsStringWithoutAnsi("for TestLayers.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestLayers._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestLayers.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("both requires and is transitively required by TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a layer from its dependencies, leaving off ZEnv") {
            val stringLayer = Console.readLine.orDie.toLayer
            val program     = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectCustom(stringLayer)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a layer from its dependencies, leaving off some environment") {
            val stringLayer = Console.readLine.orDie.toLayer
            val program     = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectSome[Has[Random] with Has[Console]](stringLayer)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("`ZLayer.wire`")(
          test("automatically constructs a layer from its dependencies") {
            val doubleLayer                      = ZLayer.succeed(100.1)
            val stringLayer: ULayer[Has[String]] = ZLayer.succeed("this string is 28 chars long")
            val intLayer = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toLayer

            val layer    = ZLayer.wire[Has[Int]](intLayer, stringLayer, doubleLayer)
            val provided = ZIO.service[Int].provideLayer(layer)
            assertM(provided)(equalTo(128))
          },
          test("reports the inclusion of non-Has types within the environment") {
            val checked = typeCheck("""ZLayer.wire[Has[String] with Int with Boolean](ZLayer.succeed("Hello"))""")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Contains non-Has types:") &&
                  containsStringWithoutAnsi("- Int") &&
                  containsStringWithoutAnsi("- Boolean")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("correctly decomposes nested, aliased intersection types") {
            type StringAlias           = String
            type HasBooleanDoubleAlias = Has[Boolean] with Has[Double]
            type Has2[A, B]            = Has[A] with Has[B]
            type FinalAlias            = Has2[Int, StringAlias] with HasBooleanDoubleAlias
            val _ = ZIO.environment[FinalAlias]

            val checked = typeCheck("ZLayer.wire[FinalAlias]()")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing Int") &&
                  containsStringWithoutAnsi("missing String") &&
                  containsStringWithoutAnsi("missing Boolean") &&
                  containsStringWithoutAnsi("missing Double")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("`ZLayer.wireSome`")(
          test("automatically constructs a layer from its dependencies, leaving off some remainder") {
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toLayer
            val program = ZIO.service[Int]

            val layer    = ZLayer.wireSome[Has[Double] with Has[Boolean], Has[Int]](intLayer, stringLayer)
            val provided = program.provideLayer(ZLayer.succeed(true) ++ ZLayer.succeed(100.1) >>> layer)
            assertM(provided)(equalTo(128))
          }
        )
      ),
      suite("ZManaged")(
        suite("`zmanaged.inject`")(
          test("automatically constructs a layer from its dependencies") {
            val doubleLayer = ZLayer.succeed(100.1)
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer =
              (for {
                str    <- ZManaged.service[String]
                double <- ZManaged.service[Double]
              } yield str.length + double.toInt).toLayer

            val program  = ZManaged.service[Int]
            val provided = program.inject(intLayer, stringLayer, doubleLayer)
            assertM(provided.useNow)(equalTo(128))
          },
          test("automatically memoizes non-val layers") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, Has[String]] =
              ref.update(_ + 1).as("Howdy").toLayer

            val layerA: URLayer[Has[String], Has[Int]]     = ZLayer.succeed(1)
            val layerB: URLayer[Has[String], Has[Boolean]] = ZLayer.succeed(true)

            (for {
              ref    <- Ref.make(0).toManaged
              _      <- (ZManaged.service[Int] <*> ZManaged.service[Boolean]).inject(layerA, layerB, sideEffectingLayer(ref))
              result <- ref.get.toManaged
            } yield assert(result)(equalTo(1))).useNow
          },
          test("reports missing top-level layers") {
            val program: ZManaged[Has[String] with Has[Int], Nothing, String] = ZManaged.succeed("test")
            val _                                                             = program

            val checked = typeCheck("program.inject(ZLayer.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level layers") {
            val program: ZManaged[Has[String] with Has[Int], Nothing, String] = ZManaged.succeed("test")
            val _                                                             = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestLayers._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                         = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestLayers.Fly") &&
                  containsStringWithoutAnsi("for TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestLayers._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                         = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestLayers.Spider") &&
                  containsStringWithoutAnsi("for TestLayers.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestLayers._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                         = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestLayers.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("both requires and is transitively required by TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a layer from its dependencies, leaving off ZEnv") {
            val stringLayer = Console.readLine.orDie.toLayer
            val program     = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectCustom(stringLayer)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a layer from its dependencies, leaving off some environment") {
            val stringLayer = Console.readLine.orDie.toLayer
            val program     = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectSome[Has[Random] with Has[Console]](stringLayer)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        )
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

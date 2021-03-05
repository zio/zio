package zio

import zio.console.Console
import zio.internal.macros.MacroUnitTestUtils
import zio.internal.macros.StringUtils.StringOps
import zio.random.Random
import zio.test.Assertion._
import zio.test.AssertionM.Render.param
import zio.test._
import zio.test.environment.TestConsole

import java.util.UUID

object AutoLayerSpec extends ZIOBaseSpec {

  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec: ZSpec[Environment, Failure] =
    suite("AutoLayerSpec")(
      suite("ZIO")(
        suite("`zio.provideLayer`")(
          testM("automatically constructs a layer from its dependencies") {
            val doubleLayer = ZLayer.succeed(100.1)
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer

            val program  = ZIO.service[Int]
            val provided = program.provideLayer(intLayer, stringLayer, doubleLayer)
            assertM(provided)(equalTo(128))
          },
          testM("automatically memoizes non-val layers") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, Has[String]] =
              ref.update(_ + 1).as("Howdy").toLayer

            val layerA: URLayer[Has[String], Has[Int]]     = ZLayer.succeed(1)
            val layerB: URLayer[Has[String], Has[Boolean]] = ZLayer.succeed(true)

            for {
              ref    <- Ref.make(0)
              _      <- ZIO.services[Int, Boolean].provideLayer(layerA, layerB, sideEffectingLayer(ref))
              result <- ref.get
            } yield assert(result)(equalTo(1))
          },
          testM("reports missing top-level layers") {
            val program: URIO[Has[String] with Has[Int], String] = UIO("test")
            val _                                                = program

            val checked = typeCheck("program.provideLayer(ZLayer.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          testM("reports multiple missing top-level layers") {
            val program: URIO[Has[String] with Has[Int], String] = UIO("test")
            val _                                                = program

            val checked = typeCheck("program.provideLayer()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          testM("reports missing transitive dependencies") {
            import TestLayers._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.provideLayer(OldLady.live)")
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

            val checked = typeCheck("program.provideLayer(OldLady.live, Fly.live)")
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

            val checked = typeCheck("program.provideLayer(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestLayers.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("both requires and is transitively required by TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("provideCustomLayer")(
          testM("automatically constructs a layer from its dependencies, leaving off ZEnv") {
            val stringLayer = console.getStrLn.orDie.toLayer
            val program     = ZIO.service[String].zipWith(random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.provideCustomLayer(stringLayer)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("provideSomeLayer")(
          testM("automatically constructs a layer from its dependencies, leaving off some environment") {
            val stringLayer = console.getStrLn.orDie.toLayer
            val program     = ZIO.service[String].zipWith(random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.provideSomeLayer[Random with Console](stringLayer)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("`ZLayer.fromAuto`")(
          testM("automatically constructs a layer from its dependencies") {
            val doubleLayer = ZLayer.succeed(100.1)
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer

            val layer    = ZLayer.fromAuto[Has[Int]](intLayer, stringLayer, doubleLayer)
            val provided = ZIO.service[Int].provideLayerManual(layer)
            assertM(provided)(equalTo(128))
          },
          testM("reports the inclusion of non-Has types within the environment") {
            val checked = typeCheck("""ZLayer.fromAuto[Has[String] with Int with Boolean](ZLayer.succeed("Hello"))""")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Contains non-Has types:") &&
                  containsStringWithoutAnsi("- Int") &&
                  containsStringWithoutAnsi("- Boolean")
              )
            )
          } @@ TestAspect.exceptDotty,
          testM("correctly decomposes nested, aliased intersection types") {
            type StringAlias           = String
            type HasBooleanDoubleAlias = Has[Boolean] with Has[Double]
            type Has2[A, B]            = Has[A] with Has[B]
            type FinalAlias            = Has2[Int, StringAlias] with HasBooleanDoubleAlias
            val _ = ZIO.environment[FinalAlias]

            val checked = typeCheck("ZLayer.fromAuto[FinalAlias]()")
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
        suite("`ZLayer.fromSomeAuto`")(
          testM("automatically constructs a layer from its dependencies, leaving off some remainder") {
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer    = ZIO.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer
            val program     = ZIO.service[Int]

            val layer    = ZLayer.fromSomeAuto[Has[Double] with Has[Boolean], Has[Int]](intLayer, stringLayer)
            val provided = program.provideLayerManual(ZLayer.succeed(true) ++ ZLayer.succeed(100.1) >>> layer)
            assertM(provided)(equalTo(128))
          }
        )
      ),
      suite("ZManaged")(
        suite("`zmanaged.provideLayer`")(
          testM("automatically constructs a layer from its dependencies") {
            val doubleLayer = ZLayer.succeed(100.1)
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer =
              ZManaged.services[String, Double].map { case (str, double) => str.length + double.toInt }.toLayer

            val program  = ZManaged.service[Int]
            val provided = program.provideLayer(intLayer, stringLayer, doubleLayer)
            assertM(provided.useNow)(equalTo(128))
          },
          testM("automatically memoizes non-val layers") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, Has[String]] =
              ref.update(_ + 1).as("Howdy").toLayer

            val layerA: URLayer[Has[String], Has[Int]]     = ZLayer.succeed(1)
            val layerB: URLayer[Has[String], Has[Boolean]] = ZLayer.succeed(true)

            (for {
              ref    <- Ref.make(0).toManaged_
              _      <- ZManaged.services[Int, Boolean].provideLayer(layerA, layerB, sideEffectingLayer(ref))
              result <- ref.get.toManaged_
            } yield assert(result)(equalTo(1))).useNow
          },
          testM("reports missing top-level layers") {
            val program: ZManaged[Has[String] with Has[Int], Nothing, String] = ZManaged.succeed("test")
            val _                                                             = program

            val checked = typeCheck("program.provideLayer(ZLayer.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          testM("reports multiple missing top-level layers") {
            val program: ZManaged[Has[String] with Has[Int], Nothing, String] = ZManaged.succeed("test")
            val _                                                             = program

            val checked = typeCheck("program.provideLayer()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          testM("reports missing transitive dependencies") {
            import TestLayers._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged_)
            val _                                         = program

            val checked = typeCheck("program.provideLayer(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.AutoLayerSpec.TestLayers.Fly") &&
                  containsStringWithoutAnsi("for TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          testM("reports nested missing transitive dependencies") {
            import TestLayers._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged_)
            val _                                         = program

            val checked = typeCheck("program.provideLayer(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.AutoLayerSpec.TestLayers.Spider") &&
                  containsStringWithoutAnsi("for TestLayers.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          testM("reports circular dependencies") {
            import TestLayers._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged_)
            val _                                         = program

            val checked = typeCheck("program.provideLayer(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestLayers.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("both requires and is transitively required by TestLayers.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("provideCustomLayer")(
          testM("automatically constructs a layer from its dependencies, leaving off ZEnv") {
            val stringLayer = console.getStrLn.orDie.toLayer
            val program     = ZManaged.service[String].zipWith(random.nextInt.toManaged_)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged_ *>
              program.provideCustomLayer(stringLayer)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("provideSomeLayer")(
          testM("automatically constructs a layer from its dependencies, leaving off some environment") {
            val stringLayer = console.getStrLn.orDie.toLayer
            val program     = ZManaged.service[String].zipWith(random.nextInt.toManaged_)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged_ *>
              program.provideSomeLayer[Random with Console](stringLayer)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        )
      ),
      suite("MacroUnitTestUtils") {
        suite(".getRequirements")(
          test("retrieves a list of Strings representing the types in a compound Has type") {
            type FunctionTypeHas          = Has[String => UUID]
            type NestedHas                = Has[FunctionTypeHas with Has[Task[Double]]]
            type ListAlias[A]             = List[A]
            type TypeFunction[F[_, _], A] = F[String, A]
            type Env =
              Has[Int] with FunctionTypeHas with NestedHas with Has[TypeFunction[Either, ListAlias[Int]]]

            val result = MacroUnitTestUtils.getRequirements[Env]
            val expected = List(
              "Int",
              "String => java.util.UUID",
              "zio.Has[String => java.util.UUID] with zio.Has[zio.ZIO[Any,Throwable,Double]]",
              "scala.util.Either[String,List[Int]]"
            )
            assert(result)(equalTo(expected))
          }
        )
      }
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

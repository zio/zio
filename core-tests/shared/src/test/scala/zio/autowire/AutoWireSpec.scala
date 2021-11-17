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
          test("automatically constructs a service builder from its dependencies") {
            val doubleServiceBuilder: UServiceBuilder[Double] = ZServiceBuilder.succeed(100.1)
            val stringServiceBuilder                          = ZServiceBuilder.succeed("this string is 28 chars long")
            val intServiceBuilder =
              ZServiceBuilder {
                for {
                  str    <- ZIO.service[String]
                  double <- ZIO.service[Double]
                } yield str.length + double.toInt
              }

            val program: URIO[Int, Int] = ZIO.service[Int]
            val injected: ZIO[Any, Nothing, Int] =
              program.inject(intServiceBuilder, stringServiceBuilder, doubleServiceBuilder)

            injected.map(result => assertTrue(result == 128))
          },
          test("automatically memoizes non-val service builders") {
            def sideEffectingServiceBuilder(ref: Ref[Int]): ZServiceBuilder[Any, Nothing, String] =
              ref.update(_ + 1).as("Howdy").toServiceBuilder

            val serviceBuilderA: URServiceBuilder[String, Int]     = ZServiceBuilder.succeed(1)
            val serviceBuilderB: URServiceBuilder[String, Boolean] = ZServiceBuilder.succeed(true)

            for {
              ref <- Ref.make(0)
              _ <- (ZIO.service[Int] <*> ZIO.service[Boolean])
                     .inject(serviceBuilderA, serviceBuilderB, sideEffectingServiceBuilder(ref))
              result <- ref.get
            } yield assertTrue(result == 1)
          },
          test("reports duplicate service builders") {
            val checked =
              typeCheck("ZIO.service[Int].inject(ZServiceBuilder.succeed(12), ZServiceBuilder.succeed(13))")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Int is provided by multiple service builders") &&
                  containsStringWithoutAnsi("ZServiceBuilder.succeed(12)") &&
                  containsStringWithoutAnsi("ZServiceBuilder.succeed(13)")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports unused, extra service builders") {
            val someServiceBuilder: URServiceBuilder[Double, String] = ZServiceBuilder.succeed("hello")
            val doubleServiceBuilder: UServiceBuilder[Double]        = ZServiceBuilder.succeed(1.0)
            val _                                                    = (someServiceBuilder, doubleServiceBuilder)

            val checked =
              typeCheck(
                "ZIO.service[Int].inject(ZServiceBuilder.succeed(12), doubleServiceBuilder, someServiceBuilder)"
              )
            assertM(checked)(isLeft(containsStringWithoutAnsi("unused")))
          } @@ TestAspect.exceptDotty,
          test("reports missing top-level service builders") {
            val program: URIO[String with Int, String] = UIO("test")
            val _                                      = program

            val checked = typeCheck("program.inject(ZServiceBuilder.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level service builders") {
            val program: URIO[String with Int, String] = UIO("test")
            val _                                      = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestServiceBuilder._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestServiceBuilder.Fly") &&
                  containsStringWithoutAnsi("for TestServiceBuilder.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestServiceBuilder._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestServiceBuilder.Spider") &&
                  containsStringWithoutAnsi("for TestServiceBuilder.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestServiceBuilder._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestServiceBuilder.Fly.manEatingFly") &&
                  containsStringWithoutAnsi(
                    "both requires and is transitively required by TestServiceBuilder.OldLady.live"
                  )
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a service builder, leaving off ZEnv") {
            val stringServiceBuilder = Console.readLine.orDie.toServiceBuilder
            val program              = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectCustom(stringServiceBuilder)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a service builder, leaving off some environment") {
            val stringServiceBuilder = Console.readLine.orDie.toServiceBuilder
            val program              = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectSome[Random with Console](stringServiceBuilder)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("`ZServiceBuilder.wire`")(
          test("automatically constructs a service builder") {
            val doubleServiceBuilder = ZServiceBuilder.succeed(100.1)
            val stringServiceBuilder: UServiceBuilder[String] =
              ZServiceBuilder.succeed("this string is 28 chars long")
            val intServiceBuilder = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toServiceBuilder

            val serviceBuilder =
              ZServiceBuilder.wire[Int](intServiceBuilder, stringServiceBuilder, doubleServiceBuilder)
            val provided = ZIO.service[Int].provideServices(serviceBuilder)
            assertM(provided)(equalTo(128))
          },
          test("correctly decomposes nested, aliased intersection types") {
            type StringAlias           = String
            type HasBooleanDoubleAlias = Boolean with Double
            type And2[A, B]            = A with B
            type FinalAlias            = And2[Int, StringAlias] with HasBooleanDoubleAlias
            val _ = ZIO.environment[FinalAlias]

            val checked = typeCheck("ZServiceBuilder.wire[FinalAlias]()")
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
        suite("`ZServiceBuilder.wireSome`")(
          test("automatically constructs a service builder, leaving off some remainder") {
            val stringServiceBuilder = ZServiceBuilder.succeed("this string is 28 chars long")
            val intServiceBuilder = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toServiceBuilder
            val program = ZIO.service[Int]

            val serviceBuilder =
              ZServiceBuilder.wireSome[Double with Boolean, Int](intServiceBuilder, stringServiceBuilder)
            val provided =
              program.provideServices(
                ZServiceBuilder.succeed(true) ++ ZServiceBuilder.succeed(100.1) >>> serviceBuilder
              )
            assertM(provided)(equalTo(128))
          }
        )
      ),
      suite("ZManaged")(
        suite("`zmanaged.inject`")(
          test("automatically constructs a service builder") {
            val doubleServiceBuilder = ZServiceBuilder.succeed(100.1)
            val stringServiceBuilder = ZServiceBuilder.succeed("this string is 28 chars long")
            val intServiceBuilder =
              ZServiceBuilder {
                for {
                  str    <- ZManaged.service[String]
                  double <- ZManaged.service[Double]
                } yield str.length + double.toInt
              }

            val program  = ZManaged.service[Int]
            val provided = program.inject(intServiceBuilder, stringServiceBuilder, doubleServiceBuilder)
            assertM(provided.useNow)(equalTo(128))
          },
          test("automatically memoizes non-val service builders") {
            def sideEffectingServiceBuilder(ref: Ref[Int]): ZServiceBuilder[Any, Nothing, String] =
              ref.update(_ + 1).as("Howdy").toServiceBuilder

            val serviceBuilderA: URServiceBuilder[String, Int]     = ZServiceBuilder.succeed(1)
            val serviceBuilderB: URServiceBuilder[String, Boolean] = ZServiceBuilder.succeed(true)

            (for {
              ref <- Ref.make(0).toManaged
              _ <- (ZManaged.service[Int] <*> ZManaged.service[Boolean])
                     .inject(serviceBuilderA, serviceBuilderB, sideEffectingServiceBuilder(ref))
              result <- ref.get.toManaged
            } yield assert(result)(equalTo(1))).useNow
          },
          test("reports missing top-level service builders") {
            val program: ZManaged[String with Int, Nothing, String] = ZManaged.succeed("test")
            val _                                                   = program

            val checked = typeCheck("program.inject(ZServiceBuilder.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level service builders") {
            val program: ZManaged[String with Int, Nothing, String] = ZManaged.succeed("test")
            val _                                                   = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestServiceBuilder._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestServiceBuilder.Fly") &&
                  containsStringWithoutAnsi("for TestServiceBuilder.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestServiceBuilder._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestServiceBuilder.Spider") &&
                  containsStringWithoutAnsi("for TestServiceBuilder.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestServiceBuilder._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestServiceBuilder.Fly.manEatingFly") &&
                  containsStringWithoutAnsi(
                    "both requires and is transitively required by TestServiceBuilder.OldLady.live"
                  )
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a service builder, leaving off ZEnv") {
            val stringServiceBuilder = Console.readLine.orDie.toServiceBuilder
            val program              = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectCustom(stringServiceBuilder)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a service builder, leaving off some environment") {
            val stringServiceBuilder = Console.readLine.orDie.toServiceBuilder
            val program              = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectSome[Random with Console](stringServiceBuilder)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        )
      )
    )

  object TestServiceBuilder {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URServiceBuilder[Fly, OldLady] = ZServiceBuilder.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URServiceBuilder[Spider, Fly]          = ZServiceBuilder.succeed(new Fly {})
      def manEatingFly: URServiceBuilder[OldLady, Fly] = ZServiceBuilder.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: UServiceBuilder[Spider] = ZServiceBuilder.succeed(new Spider {})
    }
  }
}

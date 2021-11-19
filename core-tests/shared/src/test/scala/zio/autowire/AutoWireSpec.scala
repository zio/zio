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
          test("automatically constructs a provider from its dependencies") {
            val doubleProvider: UProvider[Double] = ZProvider.succeed(100.1)
            val stringProvider                    = ZProvider.succeed("this string is 28 chars long")
            val intProvider =
              ZProvider {
                for {
                  str    <- ZIO.service[String]
                  double <- ZIO.service[Double]
                } yield str.length + double.toInt
              }

            val program: URIO[Int, Int] = ZIO.service[Int]
            val injected: ZIO[Any, Nothing, Int] =
              program.inject(intProvider, stringProvider, doubleProvider)

            injected.map(result => assertTrue(result == 128))
          },
          test("automatically memoizes non-val providers") {
            def sideEffectingProvider(ref: Ref[Int]): ZProvider[Any, Nothing, String] =
              ref.update(_ + 1).as("Howdy").toProvider

            val providerA: URProvider[String, Int]     = ZProvider.succeed(1)
            val providerB: URProvider[String, Boolean] = ZProvider.succeed(true)

            for {
              ref <- Ref.make(0)
              _ <- (ZIO.service[Int] <*> ZIO.service[Boolean])
                     .inject(providerA, providerB, sideEffectingProvider(ref))
              result <- ref.get
            } yield assertTrue(result == 1)
          },
          test("reports duplicate providers") {
            val checked =
              typeCheck("ZIO.service[Int].inject(ZProvider.succeed(12), ZProvider.succeed(13))")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Int is provided by multiple providers") &&
                  containsStringWithoutAnsi("ZProvider.succeed(12)") &&
                  containsStringWithoutAnsi("ZProvider.succeed(13)")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports unused, extra providers") {
            val someProvider: URProvider[Double, String] = ZProvider.succeed("hello")
            val doubleProvider: UProvider[Double]        = ZProvider.succeed(1.0)
            val _                                        = (someProvider, doubleProvider)

            val checked =
              typeCheck(
                "ZIO.service[Int].inject(ZProvider.succeed(12), doubleProvider, someProvider)"
              )
            assertM(checked)(isLeft(containsStringWithoutAnsi("unused")))
          } @@ TestAspect.exceptDotty,
          test("reports missing top-level providers") {
            val program: URIO[String with Int, String] = UIO("test")
            val _                                      = program

            val checked = typeCheck("program.inject(ZProvider.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level providers") {
            val program: URIO[String with Int, String] = UIO("test")
            val _                                      = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestProvider._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestProvider.Fly") &&
                  containsStringWithoutAnsi("for TestProvider.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestProvider._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestProvider.Spider") &&
                  containsStringWithoutAnsi("for TestProvider.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestProvider._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestProvider.Fly.manEatingFly") &&
                  containsStringWithoutAnsi(
                    "both requires and is transitively required by TestProvider.OldLady.live"
                  )
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a provider, leaving off ZEnv") {
            val stringProvider = Console.readLine.orDie.toProvider
            val program        = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectCustom(stringProvider)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a provider, leaving off some environment") {
            val stringProvider = Console.readLine.orDie.toProvider
            val program        = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectSome[Random with Console](stringProvider)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("`ZProvider.wire`")(
          test("automatically constructs a provider") {
            val doubleProvider = ZProvider.succeed(100.1)
            val stringProvider: UProvider[String] =
              ZProvider.succeed("this string is 28 chars long")
            val intProvider = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toProvider

            val provider =
              ZProvider.wire[Int](intProvider, stringProvider, doubleProvider)
            val provided = ZIO.service[Int].provide(provider)
            assertM(provided)(equalTo(128))
          },
          test("correctly decomposes nested, aliased intersection types") {
            type StringAlias           = String
            type HasBooleanDoubleAlias = Boolean with Double
            type And2[A, B]            = A with B
            type FinalAlias            = And2[Int, StringAlias] with HasBooleanDoubleAlias
            val _ = ZIO.environment[FinalAlias]

            val checked = typeCheck("ZProvider.wire[FinalAlias]()")
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
        suite("`ZProvider.wireSome`")(
          test("automatically constructs a provider, leaving off some remainder") {
            val stringProvider = ZProvider.succeed("this string is 28 chars long")
            val intProvider = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toProvider
            val program = ZIO.service[Int]

            val provider =
              ZProvider.wireSome[Double with Boolean, Int](intProvider, stringProvider)
            val provided =
              program.provide(
                ZProvider.succeed(true) ++ ZProvider.succeed(100.1) >>> provider
              )
            assertM(provided)(equalTo(128))
          }
        )
      ),
      suite("ZManaged")(
        suite("`zmanaged.inject`")(
          test("automatically constructs a provider") {
            val doubleProvider = ZProvider.succeed(100.1)
            val stringProvider = ZProvider.succeed("this string is 28 chars long")
            val intProvider =
              ZProvider {
                for {
                  str    <- ZManaged.service[String]
                  double <- ZManaged.service[Double]
                } yield str.length + double.toInt
              }

            val program  = ZManaged.service[Int]
            val provided = program.inject(intProvider, stringProvider, doubleProvider)
            assertM(provided.useNow)(equalTo(128))
          },
          test("automatically memoizes non-val providers") {
            def sideEffectingProvider(ref: Ref[Int]): ZProvider[Any, Nothing, String] =
              ref.update(_ + 1).as("Howdy").toProvider

            val providerA: URProvider[String, Int]     = ZProvider.succeed(1)
            val providerB: URProvider[String, Boolean] = ZProvider.succeed(true)

            (for {
              ref <- Ref.make(0).toManaged
              _ <- (ZManaged.service[Int] <*> ZManaged.service[Boolean])
                     .inject(providerA, providerB, sideEffectingProvider(ref))
              result <- ref.get.toManaged
            } yield assert(result)(equalTo(1))).useNow
          },
          test("reports missing top-level providers") {
            val program: ZManaged[String with Int, Nothing, String] = ZManaged.succeed("test")
            val _                                                   = program

            val checked = typeCheck("program.inject(ZProvider.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level providers") {
            val program: ZManaged[String with Int, Nothing, String] = ZManaged.succeed("test")
            val _                                                   = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestProvider._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestProvider.Fly") &&
                  containsStringWithoutAnsi("for TestProvider.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestProvider._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestProvider.Spider") &&
                  containsStringWithoutAnsi("for TestProvider.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestProvider._
            val program: URManaged[OldLady, Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestProvider.Fly.manEatingFly") &&
                  containsStringWithoutAnsi(
                    "both requires and is transitively required by TestProvider.OldLady.live"
                  )
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a provider, leaving off ZEnv") {
            val stringProvider = Console.readLine.orDie.toProvider
            val program        = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectCustom(stringProvider)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a provider, leaving off some environment") {
            val stringProvider = Console.readLine.orDie.toProvider
            val program        = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectSome[Random with Console](stringProvider)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        )
      )
    )

  object TestProvider {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URProvider[Fly, OldLady] = ZProvider.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URProvider[Spider, Fly]          = ZProvider.succeed(new Fly {})
      def manEatingFly: URProvider[OldLady, Fly] = ZProvider.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: UProvider[Spider] = ZProvider.succeed(new Spider {})
    }
  }
}

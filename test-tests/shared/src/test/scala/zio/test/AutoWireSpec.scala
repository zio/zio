package zio.test

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test.AssertionM.Render.param

object AutoWireSpec extends ZIOBaseSpec {
  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec: ZSpec[Environment, Failure] =
    suite("AutoWireSpec")(
      suite("inject")(
        suite("meta-suite") {
          val doubleProvider = ZProvider.succeed(100.1)
          val stringProvider = ZProvider.succeed("this string is 28 chars long")
          val intProvider =
            ZProvider {
              for {
                str    <- ZIO.service[String]
                double <- ZIO.service[Double]
              } yield str.length + double.toInt
            }
          test("automatically constructs a provider") {
            val program = ZIO.environment[ZEnv] *> ZIO.service[Int]
            assertM(program)(equalTo(128))
          }.injectCustom(doubleProvider, stringProvider, intProvider)
        },
        test("reports missing top-level dependencies") {
          val program: URIO[String with Int, String] = UIO("test")
          val _                                      = program
          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).inject(ZProvider.succeed(3))""")
          assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
        } @@ TestAspect.exceptDotty,
        test("reports multiple missing top-level dependencies") {
          val program: URIO[String with Int, String] = UIO("test")
          val _                                      = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).inject()""")
          assertM(checked)(
            isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
          )
        } @@ TestAspect.exceptDotty,
        test("reports missing transitive dependencies") {
          import TestProvider._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).inject(OldLady.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoWireSpec.TestProvider.Fly") &&
                containsStringWithoutAnsi("for TestProvider.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        test("reports nested missing transitive dependencies") {
          import TestProvider._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).inject(OldLady.live, Fly.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoWireSpec.TestProvider.Spider") &&
                containsStringWithoutAnsi("for TestProvider.Fly.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        test("reports circular dependencies") {
          import TestProvider._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked =
            typeCheck(
              """test("foo")(assertM(program)(anything)).inject(OldLady.live, Fly.manEatingFly)"""
            )
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
      suite(".injectShared") {
        val addOne      = ZIO.service[Ref[Int]].flatMap(_.getAndUpdate(_ + 1))
        val refProvider = Ref.make(1).toProvider

        suite("providers are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(3))),
            test("test 4")(assertM(addOne)(equalTo(4)))
          )
        ).injectShared(refProvider) @@ TestAspect.sequential
      },
      suite(".injectCustomShared") {
        case class IntService(ref: Ref[Int]) {
          def add(int: Int): UIO[Int] = ref.getAndUpdate(_ + int)
        }

        val addOne: ZIO[IntService with Random, Nothing, Int] =
          ZIO
            .service[IntService]
            .zip(Random.nextIntBounded(2))
            .flatMap { case (ref, int) => ref.add(int) }

        val refProvider: UProvider[IntService] = Ref.make(1).map(IntService(_)).toProvider

        suite("providers are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(2))),
            test("test 4")(assertM(addOne)(equalTo(3)))
          )
        ).injectCustomShared(refProvider) @@ TestAspect.sequential
      } @@ TestAspect.exceptDotty,
      suite(".injectSomeShared") {
        val addOne =
          ZIO.service[Ref[Int]].zip(Random.nextIntBounded(2)).flatMap { case (ref, int) => ref.getAndUpdate(_ + int) }
        val refProvider = Ref.make(1).toProvider

        suite("providers are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(2))),
            test("test 4")(assertM(addOne)(equalTo(3)))
          )
        ).injectSomeShared[Random](refProvider) @@ TestAspect.sequential
      },
      suite(".injectSome") {
        test("automatically constructs a provider, leaving off TestEnvironment") {
          for {
            result <- ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
          } yield assertTrue(result == "Your Lucky Number is -1295463240")
        }.injectSome[Random](ZProvider.succeed("Your Lucky Number is"))
      },
      suite(".injectCustom") {
        test("automatically constructs a provider, leaving off TestEnvironment") {
          for {
            result <- ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
          } yield assertTrue(result == "Your Lucky Number is -1295463240")
        }.injectCustom(ZProvider.succeed("Your Lucky Number is"))
      } @@ TestAspect.exceptDotty
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

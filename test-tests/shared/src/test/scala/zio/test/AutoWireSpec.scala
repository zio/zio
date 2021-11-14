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
          val doubleServiceBuilder = ZServiceBuilder.succeed(100.1)
          val stringServiceBuilder = ZServiceBuilder.succeed("this string is 28 chars long")
          val intServiceBuilder =
            (for {
              str    <- ZIO.service[String]
              double <- ZIO.service[Double]
            } yield str.length + double.toInt).toServiceBuilder
          test("automatically constructs a service builder") {
            val program = ZIO.environment[ZEnv] *> ZIO.service[Int]
            assertM(program)(equalTo(128))
          }.injectCustom(doubleServiceBuilder, stringServiceBuilder, intServiceBuilder)
        },
        test("reports missing top-level dependencies") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program
          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).inject(ZServiceBuilder.succeed(3))""")
          assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
        } @@ TestAspect.exceptDotty,
        test("reports multiple missing top-level dependencies") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).inject()""")
          assertM(checked)(
            isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
          )
        } @@ TestAspect.exceptDotty,
        test("reports missing transitive dependencies") {
          import TestServiceBuilder._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).inject(OldLady.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoWireSpec.TestServiceBuilder.Fly") &&
                containsStringWithoutAnsi("for TestServiceBuilder.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        test("reports nested missing transitive dependencies") {
          import TestServiceBuilder._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).inject(OldLady.live, Fly.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoWireSpec.TestServiceBuilder.Spider") &&
                containsStringWithoutAnsi("for TestServiceBuilder.Fly.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        test("reports circular dependencies") {
          import TestServiceBuilder._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked =
            typeCheck(
              """test("foo")(assertM(program)(anything)).inject(OldLady.live, Fly.manEatingFly)"""
            )
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
      suite(".injectShared") {
        val addOne            = ZIO.service[Ref[Int]].flatMap(_.getAndUpdate(_ + 1))
        val refServiceBuilder = Ref.make(1).toServiceBuilder

        suite("service builders are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(3))),
            test("test 4")(assertM(addOne)(equalTo(4)))
          )
        ).injectShared(refServiceBuilder) @@ TestAspect.sequential
      },
      suite(".injectCustomShared") {
        case class IntService(ref: Ref[Int]) {
          def add(int: Int): UIO[Int] = ref.getAndUpdate(_ + int)
        }

        val addOne: ZIO[Has[IntService] with Has[Random], Nothing, Int] =
          ZIO
            .service[IntService]
            .zip(Random.nextIntBounded(2))
            .flatMap { case (ref, int) => ref.add(int) }

        val refServiceBuilder: UServiceBuilder[Has[IntService]] = Ref.make(1).map(IntService(_)).toServiceBuilder

        suite("service builders are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(2))),
            test("test 4")(assertM(addOne)(equalTo(3)))
          )
        ).injectCustomShared(refServiceBuilder) @@ TestAspect.sequential
      } @@ TestAspect.exceptDotty,
      suite(".injectSomeShared") {
        val addOne =
          ZIO.service[Ref[Int]].zip(Random.nextIntBounded(2)).flatMap { case (ref, int) => ref.getAndUpdate(_ + int) }
        val refServiceBuilder = Ref.make(1).toServiceBuilder

        suite("service builders are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(2))),
            test("test 4")(assertM(addOne)(equalTo(3)))
          )
        ).injectSomeShared[Has[Random]](refServiceBuilder) @@ TestAspect.sequential
      },
      suite(".injectSome") {
        test("automatically constructs a service builder, leaving off TestEnvironment") {
          for {
            result <- ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
          } yield assertTrue(result == "Your Lucky Number is -1295463240")
        }.injectSome[Has[Random]](ZServiceBuilder.succeed("Your Lucky Number is"))
      }
//      suite(".injectCustom") {
//        test("automatically constructs a service builder, leaving off TestEnvironment") {
//          for {
//            result <- ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
//          } yield assertTrue(result == "Your Lucky Number is -1295463240")
//        }.injectCustom(ZServiceBuilder.succeed("Your Lucky Number is"))
//      } @@ TestAspect.exceptDotty
    )

  object TestServiceBuilder {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URServiceBuilder[Has[Fly], Has[OldLady]] = ZServiceBuilder.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URServiceBuilder[Has[Spider], Has[Fly]]          = ZServiceBuilder.succeed(new Fly {})
      def manEatingFly: URServiceBuilder[Has[OldLady], Has[Fly]] = ZServiceBuilder.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: UServiceBuilder[Has[Spider]] = ZServiceBuilder.succeed(new Spider {})
    }
  }
}

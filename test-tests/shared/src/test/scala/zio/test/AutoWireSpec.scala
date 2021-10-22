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
          val doubleDeps = ZDeps.succeed(100.1)
          val stringDeps = ZDeps.succeed("this string is 28 chars long")
          val intDeps =
            (for {
              str    <- ZIO.service[String]
              double <- ZIO.service[Double]
            } yield str.length + double.toInt).toDeps
          test("automatically constructs a set of dependencies") {
            val program = ZIO.environment[ZEnv] *> ZIO.service[Int]
            assertM(program)(equalTo(128))
          }.injectCustom(doubleDeps, stringDeps, intDeps)
        },
        test("reports missing top-level dependencies") {
          val program: URIO[Has[String] with Has[Int], String] = UIO("test")
          val _                                                = program
          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).inject(ZDeps.succeed(3))""")
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
          import TestDeps._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).inject(OldLady.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoWireSpec.TestDeps.Fly") &&
                containsStringWithoutAnsi("for TestDeps.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        test("reports nested missing transitive dependencies") {
          import TestDeps._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).inject(OldLady.live, Fly.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("missing zio.test.AutoWireSpec.TestDeps.Spider") &&
                containsStringWithoutAnsi("for TestDeps.Fly.live")
            )
          )
        } @@ TestAspect.exceptDotty,
        test("reports circular dependencies") {
          import TestDeps._
          val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                                    = program

          val checked =
            typeCheck(
              """test("foo")(assertM(program)(anything)).inject(OldLady.live, Fly.manEatingFly)"""
            )
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("TestDeps.Fly.manEatingFly") &&
                containsStringWithoutAnsi("both requires and is transitively required by TestDeps.OldLady.live")
            )
          )
        } @@ TestAspect.exceptDotty
      ),
      suite(".injectShared") {
        val addOne  = ZIO.service[Ref[Int]].flatMap(_.getAndUpdate(_ + 1))
        val refDeps = Ref.make(1).toDeps

        suite("dependencies are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(3))),
            test("test 4")(assertM(addOne)(equalTo(4)))
          )
        ).injectShared(refDeps) @@ TestAspect.sequential
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

        val refDeps: UDeps[Has[IntService]] = Ref.make(1).map(IntService(_)).toDeps

        suite("dependencies are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(2))),
            test("test 4")(assertM(addOne)(equalTo(3)))
          )
        ).injectCustomShared(refDeps) @@ TestAspect.sequential
      } @@ TestAspect.exceptDotty,
      suite(".injectSomeShared") {
        val addOne =
          ZIO.service[Ref[Int]].zip(Random.nextIntBounded(2)).flatMap { case (ref, int) => ref.getAndUpdate(_ + int) }
        val refDeps = Ref.make(1).toDeps

        suite("dependencies are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(2))),
            test("test 4")(assertM(addOne)(equalTo(3)))
          )
        ).injectSomeShared[Has[Random]](refDeps) @@ TestAspect.sequential
      },
      suite(".injectSome") {
        test("automatically constructs a set of dependencies, leaving off TestEnvironment") {
          for {
            result <- ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
          } yield assertTrue(result == "Your Lucky Number is -1295463240")
        }.injectSome[Has[Random]](ZDeps.succeed("Your Lucky Number is"))
      },
      suite(".injectCustom") {
        test("automatically constructs a set of dependencies, leaving off TestEnvironment") {
          for {
            result <- ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
          } yield assertTrue(result == "Your Lucky Number is -1295463240")
        }.injectCustom(ZDeps.succeed("Your Lucky Number is"))
      } @@ TestAspect.exceptDotty
    )

  object TestDeps {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URDeps[Has[Fly], Has[OldLady]] = ZDeps.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URDeps[Has[Spider], Has[Fly]]          = ZDeps.succeed(new Fly {})
      def manEatingFly: URDeps[Has[OldLady], Has[Fly]] = ZDeps.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: UDeps[Has[Spider]] = ZDeps.succeed(new Spider {})
    }
  }
}

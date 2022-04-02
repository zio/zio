package zio.test

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test.AssertionM.Render.param

object AutoWireSpec extends ZIOBaseSpec {
  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec =
    suite("AutoWireSpec")(
      suite("inject")(
        suite("meta-suite") {
          val doubleLayer = ZLayer.succeed(100.1)
          val stringLayer = ZLayer.succeed("this string is 28 chars long")
          val intLayer =
            ZLayer {
              for {
                str    <- ZIO.service[String]
                double <- ZIO.service[Double]
              } yield str.length + double.toInt
            }
          test("automatically constructs a layer") {
            val program = ZIO.service[Int]
            assertM(program)(equalTo(128))
          }
            .provide(doubleLayer, stringLayer, intLayer)
        },
        test("reports missing top-level dependencies") {
          val program: URIO[String with Int, String] = ZIO.succeed("test")
          val _                                      = program
          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).provide(ZLayer.succeed(3))""")
          assertM(checked)(isLeft(containsStringWithoutAnsi("String")))
        } @@ TestAspect.exceptScala3,
        test("reports multiple missing top-level dependencies") {
          val program: URIO[String with Int, String] = ZIO.succeed("test")
          val _                                      = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).provide()""")
          assertM(checked)(
            isLeft(containsStringWithoutAnsi("String") && containsStringWithoutAnsi("Int"))
          )
        } @@ TestAspect.exceptScala3,
        test("reports missing transitive dependencies") {
          import TestLayer._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked = typeCheck("""test("foo")(assertM(program)(anything)).provide(OldLady.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("zio.test.AutoWireSpec.TestLayer.Fly") &&
                containsStringWithoutAnsi("Required by TestLayer.OldLady.live")
            )
          )
        } @@ TestAspect.exceptScala3,
        test("reports nested missing transitive dependencies") {
          import TestLayer._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked =
            typeCheck("""test("foo")(assertM(program)(anything)).provide(OldLady.live, Fly.live)""")
          assertM(checked)(
            isLeft(
              containsStringWithoutAnsi("zio.test.AutoWireSpec.TestLayer.Spider") &&
                containsStringWithoutAnsi("Required by TestLayer.Fly.live")
            )
          )
        } @@ TestAspect.exceptScala3,
        test("reports circular dependencies") {
          import TestLayer._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked =
            typeCheck(
              """test("foo")(assertM(program)(anything)).provide(OldLady.live, Fly.manEatingFly)"""
            )
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
      suite(".provideShared") {
        val addOne   = ZIO.service[Ref[Int]].flatMap(_.getAndUpdate(_ + 1))
        val refLayer = Ref.make(1).toLayer

        suite("layers are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(3))),
            test("test 4")(assertM(addOne)(equalTo(4)))
          )
        ).provideShared(refLayer) @@ TestAspect.sequential
      },
      suite(".provideCustomShared") {
        case class IntService(ref: Ref[Int]) {
          def add(int: Int): UIO[Int] = ref.getAndUpdate(_ + int)
        }

        val addOne: ZIO[IntService, Nothing, Int] =
          ZIO
            .serviceWithZIO[IntService](_.add(1))

        val refLayer: ULayer[IntService] = Ref.make(1).map(IntService(_)).toLayer

        suite("layers are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertM(addOne)(equalTo(1))),
            test("test 2")(assertM(addOne)(equalTo(2)))
          ),
          suite("suite 2")(
            test("test 3")(assertM(addOne)(equalTo(3))),
            test("test 4")(assertM(addOne)(equalTo(4)))
          )
        ).provideShared(refLayer) @@ TestAspect.sequential
      } @@ TestAspect.exceptScala3
    )

  object TestLayer {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URLayer[Fly, OldLady] = ZLayer.succeed(new OldLady {
        override def willDie: UIO[Boolean] = ZIO.succeed(false)
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

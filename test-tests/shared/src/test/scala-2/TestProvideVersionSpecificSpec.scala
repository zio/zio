package zio.test

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._

object TestProvideVersionSpecificSpec extends ZIOBaseSpec {
  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(_.unstyled.contains(element))

  def spec =
    suite("TestProvideVersionSpecificSpec")(
      suite(".provide")(
        test("reports missing top-level dependencies") {
          val program: URIO[String with Int, String] = ZIO.succeed("test")
          val _                                      = program
          val checked =
            typeCheck("""test("foo")(assertZIO(program)(anything)).provide(ZLayer.succeed(3))""")
          assertZIO(checked)(isLeft(containsStringWithoutAnsi("String")))
        },
        test("reports multiple missing top-level dependencies") {
          val program: URIO[String with Int, String] = ZIO.succeed("test")
          val _                                      = program

          val checked = typeCheck("""test("foo")(assertZIO(program)(anything)).provide()""")
          assertZIO(checked)(
            isLeft(containsStringWithoutAnsi("String") && containsStringWithoutAnsi("Int"))
          )
        },
        test("reports missing transitive dependencies") {
          import TestLayer._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked = typeCheck("""test("foo")(assertZIO(program)(anything)).provide(OldLady.live)""")
          assertZIO(checked)(
            isLeft(
              containsStringWithoutAnsi("zio.test.TestProvideSpec.TestLayer.Fly") &&
                containsStringWithoutAnsi("Required by TestLayer.OldLady.live")
            )
          )
        },
        test("reports nested missing transitive dependencies") {
          import TestLayer._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked =
            typeCheck("""test("foo")(assertZIO(program)(anything)).provide(OldLady.live, Fly.live)""")
          assertZIO(checked)(
            isLeft(
              containsStringWithoutAnsi("zio.test.TestProvideSpec.TestLayer.Spider") &&
                containsStringWithoutAnsi("Required by TestLayer.Fly.live")
            )
          )
        },
        test("reports circular dependencies") {
          import TestLayer._
          val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
          val _                               = program

          val checked =
            typeCheck(
              """test("foo")(assertZIO(program)(anything)).provide(OldLady.live, Fly.manEatingFly)"""
            )
          assertZIO(checked)(
            isLeft(
              containsStringWithoutAnsi("TestLayer.Fly.manEatingFly") &&
                containsStringWithoutAnsi("OldLady.live") &&
                containsStringWithoutAnsi(
                  "A layer simultaneously requires and is required by another"
                )
            )
          )
        }
      )
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

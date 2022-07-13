package zio.test

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test.TestProvideSpecTypes.{IntService, StringService}

object TestProvideSpec extends ZIOBaseSpec {
  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(_.unstyled.contains(element))

  def spec =
    suite("TestProvideSpec")(
      suite(".provide")(
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
            assertZIO(program)(equalTo(128))
          }
            .provide(doubleLayer, stringLayer, intLayer)
        }
      ),
      suite(".provideSome") {
        val stringLayer = ZLayer.succeed("10")

        val myTest: Spec[Int, Nothing] = test("provides some") {
          ZIO.environment[Int with String].map { env =>
            assertTrue(env.get[String].toInt == env.get[Int])
          }
        }.provideSome[Int](stringLayer)

        myTest.provide(ZLayer.succeed(10))
      },
      suite(".provideShared") {
        val addOne   = ZIO.service[Ref[Int]].flatMap(_.getAndUpdate(_ + 1))
        val refLayer = ZLayer(Ref.make(1))

        suite("layers are shared between tests and suites")(
          suite("suite 1")(
            test("test 1")(assertZIO(addOne)(equalTo(1))),
            test("test 2")(assertZIO(addOne)(equalTo(2)))
          ),
          suite("suite 4")(
            test("test 3")(assertZIO(addOne)(equalTo(3))),
            test("test 4")(assertZIO(addOne)(equalTo(4)))
          )
        ).provideShared(refLayer) @@ TestAspect.sequential
      },
      suite(".provideSomeShared") {

        val addOne: ZIO[IntService, Nothing, Int] =
          ZIO.serviceWithZIO[IntService](_.add(1))

        val appendBang: ZIO[StringService, Nothing, String] =
          ZIO.serviceWithZIO[StringService](_.append("!"))

        val intService: ULayer[IntService] = ZLayer(Ref.make(0).map(IntService(_)))
        val stringService: ULayer[StringService] =
          ZLayer(Ref.make("Hello").map(StringService(_)).debug("MAKING"))

        def customTest(int: Int) =
          test(s"test $int") {
            for {
              x   <- addOne
              str <- appendBang
            } yield assertTrue(x == int && str == s"Hello!")
          }

        suite("layers are shared between tests and suites")(
          suite("suite 1")(
            customTest(1),
            customTest(2)
          ),
          suite("suite 4")(
            customTest(3),
            customTest(4)
          )
        )
          .provideSomeShared[StringService](intService)
          .provide(stringService) @@ TestAspect.sequential
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

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
        },
        test("reports ambiguous layers") {
          import TestLayer._
          val gp: ULayer[Repository]    = GreenplumRepository.live
          val mongo: ULayer[Repository] = MongoRepository.live

          val program: URIO[Repository, String] = ZIO.succeed("test")
          val _                                 = (gp, mongo, program)

          val checked = typeCheck("""test("foo")(assertZIO(program)(anything)).provide(gp, mongo)""")
          assertZIO(checked)(isLeft(containsStringWithoutAnsi("Ambiguous layers")))
        } @@ TestAspect.exceptScala3,
        test("reports ambiguous layers which are subtypes of some requirement") {
          import TestLayer._
          val gp    = GreenplumRepository.live
          val mongo = MongoRepository.live
          val repo  = RepositoryLive.live

          val program: URIO[Repository, String] = ZIO.succeed("test")
          val _                                 = (gp, mongo, repo, program)

          val checked = typeCheck("""test("foo")(assertZIO(program)(anything)).provide(repo, gp, mongo)""")
          assertZIO(checked)(isLeft(containsStringWithoutAnsi("Ambiguous layers")))
        } @@ TestAspect.exceptScala3,
        test("reports ambiguous layers which are subtypes of some transitive requirement") {
          import TestLayer._
          val gp    = GreenplumRepository.live
          val mongo = MongoRepository.live
          val repo  = RepositoryLive.live

          val program: URIO[Service, String] = ZIO.succeed("test")
          val _                              = (gp, mongo, repo, program)

          val checked =
            typeCheck("""test("foo")(assertZIO(program)(anything)).provide(ServiceLive.live, repo, gp, mongo)""")
          assertZIO(checked)(isLeft(containsStringWithoutAnsi("Ambiguous layers")))
        } @@ TestAspect.exceptScala3,
        test("reports missing top-level dependencies") {
          val program: URIO[String with Int, String] = ZIO.succeed("test")
          val _                                      = program
          val checked =
            typeCheck("""test("foo")(assertZIO(program)(anything)).provide(ZLayer.succeed(3))""")
          assertZIO(checked)(isLeft(containsStringWithoutAnsi("String")))
        } @@ TestAspect.exceptScala3,
        test("reports multiple missing top-level dependencies") {
          val program: URIO[String with Int, String] = ZIO.succeed("test")
          val _                                      = program

          val checked = typeCheck("""test("foo")(assertZIO(program)(anything)).provide()""")
          assertZIO(checked)(
            isLeft(containsStringWithoutAnsi("String") && containsStringWithoutAnsi("Int"))
          )
        } @@ TestAspect.exceptScala3,
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
        } @@ TestAspect.exceptScala3,
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
        } @@ TestAspect.exceptScala3,
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
        } @@ TestAspect.exceptScala3
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

    trait Repository

    case class MongoRepository() extends Repository
    object MongoRepository {
      val live: ULayer[MongoRepository] = ZLayer.fromFunction(MongoRepository.apply _)
    }

    case class GreenplumRepository() extends Repository
    object GreenplumRepository {
      val live: ULayer[GreenplumRepository] = ZLayer.fromFunction(GreenplumRepository.apply _)
    }

    case class RepositoryLive(mongo: MongoRepository, gp: GreenplumRepository) extends Repository
    object RepositoryLive {
      val live: URLayer[MongoRepository with GreenplumRepository, RepositoryLive] =
        ZLayer.fromFunction(RepositoryLive.apply _)
    }

    trait Service
    case class ServiceLive(repo: Repository) extends Service
    object ServiceLive {
      val live: URLayer[Repository, ServiceLive] = ZLayer.fromFunction(ServiceLive.apply _)
    }

  }

}

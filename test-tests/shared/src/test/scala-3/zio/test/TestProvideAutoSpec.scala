package zio.test

import zio.*
import zio.test.Assertion.*
import zio.test.TestProvideSpecTypes.{IntService, StringService}

object TestProvideAutoSpec extends ZIOBaseSpec {
  def spec =
    suite("TestProvideAutoSpec")(
      suite(".provideSomeAuto") {
        val stringLayer = ZLayer.succeed("10")

        val myTest = test("provides some") {
          ZIO.environment[Int with String].map { env =>
            assertTrue(env.get[String].toInt == env.get[Int])
          }
        }.provideSomeAuto(stringLayer)

        myTest.provide(ZLayer.succeed(10))
      },
      suite(".provideSomeSharedAuto") {

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
          .provideSomeSharedAuto(intService)
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

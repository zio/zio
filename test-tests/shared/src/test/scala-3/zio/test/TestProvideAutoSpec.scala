package zio.test

import zio.*
import zio.test.Assertion.*

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
      }
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

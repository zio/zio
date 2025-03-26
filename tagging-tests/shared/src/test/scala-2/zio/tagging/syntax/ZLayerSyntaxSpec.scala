package zio.tagging.syntax

import zio._
import zio.tagging.tag.@@
import zio.test.Assertion.equalTo
import zio.test._

object ZLayerSyntaxSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZLayerSyntax")(
      test("requireTagged") {
        val zio = ZIO
          .service[String]
          .provide(
            ZLayer.succeed(1).tagged[Alfa],
            ZLayer.succeed(2).tagged[Beta],
            ZLayer
              .fromFunction((i: Int) => i * 100L)
              .requireTagged[Int, Alfa],
            ZLayer
              .fromFunction((i: Int, u: Long) => (i + u).toString)
              .requireTagged[Int, Beta]
          )
        assertZIO(zio)(equalTo("102"))
      },
      test("requireTagged for the same layer instance") {
        val env = ZLayer.make[ServiceAlfa with ServiceBeta](
          makeConfig("Alice").tagged[Alfa],
          myDependencyLive
            .tagged[Alfa]
            .requireTagged[Config, Alfa],
          serviceAlfaLive.requireTagged[Dependency, Alfa],
          makeConfig("Bob").tagged[Beta],
          myDependencyLive.tagged[Beta].requireTagged[Config, Beta],
          serviceBetaLive.requireTagged[Dependency, Beta]
        )

        val effect = for {
          sentence1 <- ZIO.serviceWithZIO[ServiceAlfa](_.sayHello())
          sentence2 <- ZIO.serviceWithZIO[ServiceBeta](_.sayHi())
        } yield assertTrue(
          sentence1 == "Hello, Alice!",
          sentence2 == "Hi, Bob!"
        )

        effect.provideLayer(env)
      },
      test("requireTagged for the same layer instance (without relying on ZLayer.make)") {
        // The types are explicitly widened to make it impossible for `ZLayer.make` to guess which layers should be injected.
        val serviceAlfaTagged: ZLayer[DependencyAlfa with DependencyBeta, Nothing, ServiceAlfa] =
          serviceAlfaLive.requireTagged[Dependency, Alfa]
        val serviceBetaTagged: ZLayer[DependencyAlfa with DependencyBeta, Nothing, ServiceBeta] =
          serviceBetaLive.requireTagged[Dependency, Beta]
        val env = ZLayer.make[ServiceAlfa with ServiceBeta](
          makeConfig("Alice").tagged[Alfa],
          myDependencyLive.tagged[Alfa].requireTagged[Config, Alfa],
          serviceAlfaTagged,
          makeConfig("Bob").tagged[Beta],
          myDependencyLive.tagged[Beta].requireTagged[Config, Beta],
          serviceBetaTagged
        )

        val effect = for {
          sentence1 <- ZIO.serviceWithZIO[ServiceAlfa](_.sayHello())
          sentence2 <- ZIO.serviceWithZIO[ServiceBeta](_.sayHi())
        } yield assertTrue(
          sentence1 == "Hello, Alice!",
          sentence2 == "Hi, Bob!"
        )

        effect.provideLayer(env)
      },
      test("requireTagged should provide expected environment to the wrapped layer (without extra dependencies)") {
        type EnvContent = Boolean
        val envSize = 1
        type Env = ZEnvironment[EnvContent]

        val baseLayer = ZLayer
          .succeed(true)
          .tagged[Alfa]
          .and(ZLayer.succeed(false).tagged[Beta])
        val alfaLayer =
          ZLayer
            .fromZIO(ZIO.environment[EnvContent])
            .requireTagged[Boolean, Alfa]
            .tagged[Alfa]
        val betaLayer =
          ZLayer
            .fromZIO(ZIO.environment[EnvContent])
            .requireTagged[Boolean, Beta]
            .tagged[Beta]
        val env = baseLayer.to(alfaLayer.and(betaLayer))

        val effect = for {
          envAlfa <- ZIO.service[Env @@ Alfa]
          envBeta <- ZIO.service[Env @@ Beta]
        } yield assertTrue(
          envAlfa.size == envSize,
          envAlfa.get[Boolean],
          envBeta.size == envSize,
          !envBeta.get[Boolean]
        )

        effect.provideLayer(env)
      },
      test("requireTagged should provide expected environment to the wrapped layer (with extra dependencies)") {
        type EnvContent = Boolean with Int
        val envSize = 2
        type Env = ZEnvironment[EnvContent]

        val baseLayer = ZLayer
          .succeed(true)
          .tagged[Alfa]
          .and(ZLayer.succeed(false).tagged[Beta])
          .and(ZLayer.succeed(123))
        val alfaLayer =
          ZLayer
            .fromZIO(ZIO.environment[EnvContent])
            .requireTagged[Boolean, Alfa]
            .tagged[Alfa]
        val betaLayer =
          ZLayer
            .fromZIO(ZIO.environment[EnvContent])
            .requireTagged[Boolean, Beta]
            .tagged[Beta]
        val env = baseLayer.to(alfaLayer.and(betaLayer))

        val effect = for {
          envAlfa <- ZIO.service[Env @@ Alfa]
          envBeta <- ZIO.service[Env @@ Beta]
        } yield assertTrue(
          envAlfa.size == envSize,
          envAlfa.get[Boolean],
          envBeta.size == envSize,
          !envBeta.get[Boolean]
        )

        effect.provideLayer(env)
      },
      test("!>>> works") {
        for {
          r <- typeCheck(
                 """
                   |import zio.{TaskLayer, URLayer, RLayer}
                   |import zio.tagging.tag.@@
                   |
                   |trait A
                   |trait B
                   |trait C
                   |
                   |val a: TaskLayer[(A @@ "tag")] = ???
                   |val abc: URLayer[(A @@ "tag") with B, C] = ???
                   |val bc: RLayer[B, C] = a !>>> abc
                   |""".stripMargin
               )
        } yield assertTrue(r.isRight)
      }
    )

  private trait Alfa
  private trait Beta

  private case class Config(name: String)

  private type DependencyAlfa = Dependency @@ Alfa
  private type DependencyBeta = Dependency @@ Beta

  private class Dependency(config: Config) {
    def getName: String = config.name
  }

  private trait ServiceAlfa {
    def sayHello(): UIO[String]
  }

  private trait ServiceBeta {
    def sayHi(): UIO[String]
  }

  private class ServiceAlfaLive(dependency: Dependency) extends ServiceAlfa {
    override def sayHello(): UIO[String] = ZIO.succeed(s"Hello, ${dependency.getName}!")
  }

  private class ServiceBetaLive(dependency: Dependency) extends ServiceBeta {
    override def sayHi(): UIO[String] = ZIO.succeed(s"Hi, ${dependency.getName}!")
  }

  private def makeConfig(name: String): ULayer[Config]          = ZLayer.succeed(Config(name))
  private val myDependencyLive: URLayer[Config, Dependency]     = ZLayer.fromFunction(new Dependency(_))
  private val serviceAlfaLive: URLayer[Dependency, ServiceAlfa] = ZLayer.fromFunction(new ServiceAlfaLive(_))
  private val serviceBetaLive: URLayer[Dependency, ServiceBeta] = ZLayer.fromFunction(new ServiceBetaLive(_))
}

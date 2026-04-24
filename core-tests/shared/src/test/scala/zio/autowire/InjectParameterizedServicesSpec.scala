package zio.autowire

import zio._
import zio.test._

// https://github.com/kitlangton/zio-magic/issues/76
object InjectParameterizedServicesSpec extends ZIOBaseSpec {

  def spec = suite("Samples")(
    test("compiles with ParameterisedServiceImpl1 direct usage") {
      ParameterizedService.something[String].as(assertCompletes)
    }.provide(ParameterisedServiceWithoutTypeAlias.live),
    test("compiles using the type alias directly") {
      val huh: ZIO[ParameterizedService[ParameterizedServiceWithTypeAlias.Alias], Nothing, TestResult] =
        ParameterizedService.something[ParameterizedServiceWithTypeAlias.Alias].as(assertCompletes)
      huh
    }.provide(ParameterizedServiceWithTypeAlias.live),
    test("fails to compile using the type directly") {
      val huh: ZIO[ParameterizedService[String], Nothing, TestResult] =
        ParameterizedService.something[String].as(assertCompletes)
      huh
    }.provide(ParameterizedServiceWithTypeAlias.live),
    test("compile using the type directly if not using wire macro") {
      ParameterizedService.something[String].as(assertCompletes)
    }.provideLayer(ParameterizedServiceWithTypeAlias.live)
  ) @@ TestAspect.exceptScala3

  trait ParameterizedService[A] {
    def something: UIO[Unit]
  }

  object ParameterizedService {
    def something[A](implicit tag: EnvironmentTag[A]): ZIO[ParameterizedService[A], Nothing, Unit] =
      ZIO.serviceWithZIO[ParameterizedService[A]](_.something)
  }

  object ParameterizedServiceWithTypeAlias {
    type Alias = String

    val live: ULayer[ParameterizedService[Alias]] =
      ZLayer.succeed(new ParameterizedService[Alias] {
        override def something: UIO[Unit] = ZIO.unit
      })
  }

  object ParameterisedServiceWithoutTypeAlias {
    val live: ULayer[ParameterizedService[String]] =
      ZLayer.succeed(new ParameterizedService[String] {
        override def something: UIO[Unit] = ZIO.unit
      })
  }

}

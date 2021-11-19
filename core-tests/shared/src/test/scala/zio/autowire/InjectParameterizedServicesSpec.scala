package zio.autowire

import zio.{Tag, UIO, ZIO, ZProvider}
import zio.test._
import zio.UProvider

// https://github.com/kitlangton/zio-magic/issues/76
object InjectParameterizedServicesSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("Samples")(
    test("compiles with ParameterisedServiceImpl1 direct usage") {
      ParameterizedService.something[String].as(assertCompletes)
    }.inject(ParameterisedServiceWithoutTypeAlias.live),
    test("compiles using the type alias directly") {
      val huh: ZIO[ParameterizedService[ParameterizedServiceWithTypeAlias.Alias], Nothing, TestResult] =
        ParameterizedService.something[ParameterizedServiceWithTypeAlias.Alias].as(assertCompletes)
      huh
    }.inject(ParameterizedServiceWithTypeAlias.live),
    test("fails to compile using the type directly") {
      val huh: ZIO[ParameterizedService[String], Nothing, TestResult] =
        ParameterizedService.something[String].as(assertCompletes)
      huh
    }.inject(ParameterizedServiceWithTypeAlias.live),
    test("compile using the type directly if not using wire macro") {
      ParameterizedService.something[String].as(assertCompletes)
    }.provide(ParameterizedServiceWithTypeAlias.live)
  ) @@ TestAspect.exceptDotty

  trait ParameterizedService[A] {
    def something: UIO[Unit]
  }

  object ParameterizedService {
    def something[A: Tag]: ZIO[ParameterizedService[A], Nothing, Unit] =
      ZIO.serviceWithZIO[ParameterizedService[A]](_.something)
  }

  object ParameterizedServiceWithTypeAlias {
    type Alias = String

    val live: UProvider[ParameterizedService[Alias]] =
      ZProvider.succeed(new ParameterizedService[Alias] {
        override def something: UIO[Unit] = ZIO.unit
      })
  }

  object ParameterisedServiceWithoutTypeAlias {
    val live: UProvider[ParameterizedService[String]] =
      ZProvider.succeed(new ParameterizedService[String] {
        override def something: UIO[Unit] = ZIO.unit
      })
  }

}

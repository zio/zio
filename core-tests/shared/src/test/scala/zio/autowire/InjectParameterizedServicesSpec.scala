package zio.autowire

import zio.{Has, Tag, UIO, ZIO, ZLayer}
import zio.test._
import zio.ULayer

// https://github.com/kitlangton/zio-magic/issues/76
object InjectParameterizedServicesSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("Samples")(
    test("compiles with ParameterisedServiceImpl1 direct usage") {
      ParameterizedService.something[Has[String]].as(assertCompletes)
    }.inject(ParameterisedServiceWithoutTypeAlias.live),
    test("compiles using the type alias directly") {
      val huh: ZIO[Has[ParameterizedService[ParameterizedServiceWithTypeAlias.Alias]], Nothing, TestResult] =
        ParameterizedService.something[ParameterizedServiceWithTypeAlias.Alias].as(assertCompletes)
      huh
    }.inject(ParameterizedServiceWithTypeAlias.live),
    test("fails to compile using the type directly") {
      val huh: ZIO[Has[ParameterizedService[Has[String]]], Nothing, TestResult] =
        ParameterizedService.something[Has[String]].as(assertCompletes)
      huh
    }.inject(ParameterizedServiceWithTypeAlias.live),
    test("compile using the type directly if not using wire macro") {
      ParameterizedService.something[Has[String]].as(assertCompletes)
    }.provideLayer(ParameterizedServiceWithTypeAlias.live)
  )

  trait ParameterizedService[A] {
    def something: UIO[Unit]
  }

  object ParameterizedService {
    def something[A: Tag]: ZIO[Has[ParameterizedService[A]], Nothing, Unit] =
      ZIO.serviceWith[ParameterizedService[A]](_.something)
  }

  object ParameterizedServiceWithTypeAlias {
    type Alias = Has[String]

    val live: ULayer[Has[ParameterizedService[Alias]]] = ZLayer.succeed(new ParameterizedService[Alias] {
      override def something: UIO[Unit] = ZIO.unit
    })
  }

  object ParameterisedServiceWithoutTypeAlias {
    val live: ULayer[Has[ParameterizedService[Has[String]]]] = ZLayer.succeed(new ParameterizedService[Has[String]] {
      override def something: UIO[Unit] = ZIO.unit
    })
  }

}

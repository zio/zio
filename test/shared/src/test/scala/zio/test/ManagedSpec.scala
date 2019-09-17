package zio.test

import scala.concurrent.Future

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestUtils.{ label, succeeded }
import zio.test.Spec.SuiteCase
import zio.test.Spec.TestCase
import zio.test.Spec.EffectCase

object ManagedSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(managedResourcesCanBeUsedAcrossTests, "managed resources can be used across tests")
  )

  def provideManaged[R, E, L, S](r: Managed[Nothing, R])(spec: Spec[R, E, L, ZIO[R, E, S]]): Spec[Any, E, L, ZIO[Any, E, S]] = {
    def loop(r: R, spec: Spec[R, E, L, ZIO[R, E, S]]): ZIO[Any, E, Spec[Any, E, L, ZIO[Any, E, S]]] = spec.caseValue match {
      case SuiteCase(label, specs, exec) => ZIO.foreach(specs)(loop(r, _)).map(z => Spec.suite(label, z.toVector, exec))
      case TestCase(label, test) => test.map(r => Spec.test(label, ZIO.succeed(r))).provide(r)
      case EffectCase(effect) => effect.provide(r).flatMap(specs => loop(r, Spec(specs)))
    }
    spec.caseValue match {
      case SuiteCase(label, specs, exec) => Spec.effect(r.use(r => ZIO.foreach(specs)(loop(r, _))).map(z => SuiteCase(label, z.toVector, exec)))
      case c @ TestCase(_, _) => Spec.effect(r.use(r => loop(r, Spec(c))).map(_.caseValue))
      case EffectCase(effect) => Spec.effect(r.use(r => effect.provide(r).flatMap(specs => loop(r, Spec(specs)))).map(_.caseValue))
    }
  }

  def managedResourcesCanBeUsedAcrossTests: Future[Boolean] =
    unsafeRunToFuture {
      val spec = suite("managed suite")(
          testM("first test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(2))
          },
          testM("second test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(3))
          },
          testM("third test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(4))
          }
        )
        
      val provided = provideManaged(Ref.make(1).toManaged(_.set(-10)))(spec)
      succeeded(provided)
    }
}

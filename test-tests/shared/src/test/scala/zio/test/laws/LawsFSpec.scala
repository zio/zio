package zio.test.laws

import zio.ZIO
import zio.random.Random
import zio.test.Assertion._
import zio.test._

object LawsFSpec extends ZIOBaseSpec {

  val OptionGenF: GenF[Random, Option] =
    new GenF[Random, Option] {
      def apply[R1 <: Random, A](gen: Gen[R1, A]): Gen[R1, Option[A]] =
        Gen.option(gen)
    }

  trait Covariant[F[+_]] {
    def map[A, B](f: A => B): F[A] => F[B]
  }

  object Covariant extends LawfulF.Covariant[Covariant] {

    val identityLaw = new LawsF.Covariant.Law1[Covariant]("identityLaw") {
      def apply[F[+_]: Covariant, A](fa: F[A]): TestResult = {
        val actual   = fa.map(identity)
        val expected = fa
        assert(actual)(equalTo(expected))
      }
    }

    val compositionLaw = new ZLawsF.Covariant.Law3Gen[Covariant, Any]("compositionLaw") {
      def apply[F[+_]: Covariant, R, A, B, C](a: Gen[R, A], b: Gen[R, B], c: Gen[R, C])(
        gen: GenF[R, F]
      ): ZIO[R, Nothing, TestResult] = {
        val fa = gen(a)
        val f  = Gen.function[R, A, B](b)
        val g  = Gen.function[R, B, C](c)
        check(fa, f, g) { (fa, f, g) =>
          val actual   = fa.map(f).map(g)
          val expected = fa.map(f andThen g)
          assert(actual)(equalTo(expected))
        }
      }
    }

    val compositionLaw2 = new ZLawsF.Covariant.Law3Specialized[Covariant]("compositionLaw") {
      def apply[F[+_]: Covariant, A, B, C](fa: F[A], f: A => B, g: B => C): TestResult =
        assert(fa.map(f).map(g))(equalTo(fa.map(f andThen g)))
    }

    val laws = identityLaw + compositionLaw

    implicit val OptionCovariant: Covariant[Option] =
      new Covariant[Option] {
        def map[A, B](f: A => B): Option[A] => Option[B] = {
          case Some(a) => Some(f(a))
          case None    => None
        }
      }

    implicit class CovariantSyntax[F[+_], A](private val self: F[A]) extends AnyVal {
      def map[B](f: A => B)(implicit F: Covariant[F]): F[B] =
        F.map(f)(self)
    }
  }

  def spec = suite("LawsFSpec")(
    suite("covariantLaws")(
      testM("option") {
        ZIO(assertCompletes)
        checkAllLaws(Covariant)(OptionGenF)
      }
    )
  )
}

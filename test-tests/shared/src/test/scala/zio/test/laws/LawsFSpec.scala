package zio.test.laws

import zio.random.Random
import zio.test._

object LawsFSpec extends ZIOBaseSpec {

  def equalTo[A: Equal](expected: A): Assertion[A] =
    Assertion.assertion("equalTo")(Assertion.Render.param(expected))(_ === expected)

  implicit class AssertEqualToSyntax[A](private val self: A) extends AnyVal {
    def <->(that: A)(implicit eq: Equal[A]): TestResult =
      assert(self)(equalTo(that))
  }

  trait Equal[-A] {
    def equal(l: A, r: A): Boolean
  }

  object Equal {

    def apply[A](implicit equal: Equal[A]): Equal[A] =
      equal

    implicit def deriveEqual[F[_]: EqualF, A: Equal]: Equal[F[A]] =
      EqualF[F].deriveEqual(Equal[A])

    implicit val intEqual: Equal[Int] =
      _ == _
  }

  implicit class EqualSyntax[A](private val self: A) extends AnyVal {
    def ===(that: A)(implicit eq: Equal[A]): Boolean =
      eq.equal(self, that)
  }

  trait EqualF[F[_]] {
    def deriveEqual[A](equal: Equal[A]): Equal[F[A]]
  }

  object EqualF extends CovariantEqualF {

    def apply[F[_]](implicit equalF: EqualF[F]): EqualF[F] =
      equalF

    implicit val OptionEqualF: EqualF[Option] =
      new EqualF[Option] {
        def deriveEqual[A](A: Equal[A]): Equal[Option[A]] = {
          case (None, None)       => true
          case (Some(l), Some(r)) => A.equal(l, r)
          case _                  => false
        }
      }
  }

  val OptionGenF: GenF[Random, Option] =
    new GenF[Random, Option] {
      def apply[R1 <: Random, A](gen: Gen[R1, A]): Gen[R1, Option[A]] =
        Gen.option(gen)
    }

  trait Covariant[F[+_]] {
    def map[A, B](f: A => B): F[A] => F[B]
  }

  object Covariant extends LawfulF.Covariant[Covariant with EqualF, Equal] {

    val identityLaw = new LawsF.Covariant.Law1[Covariant with EqualF, Equal]("identityLaw") {
      def apply[F[+_], A](fa: F[A])(implicit F: Covariant[F] with EqualF[F], A: Equal[A]): TestResult =
        fa.map(identity) <-> fa
    }

    val compositionLaw = new ZLawsF.Covariant.Law3Function[Covariant with EqualF, Equal]("compositionLaw") {
      def apply[F[+_], A, B, C](
        fa: F[A],
        f: A => B,
        g: B => C
      )(implicit F: Covariant[F] with EqualF[F], A: Equal[A], B: Equal[B], C: Equal[C]): TestResult =
        fa.map(f).map(g) <-> fa.map(f andThen g)
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

  trait CovariantEqualF {
    implicit def CovariantEqualF[F[+_]](
      implicit covariant: Covariant[F],
      equalF: EqualF[F]
    ): Covariant[F] with EqualF[F] =
      new Covariant[F] with EqualF[F] {
        def deriveEqual[A](equal: Equal[A]): Equal[F[A]] =
          equalF.deriveEqual(equal)
        def map[A, B](f: A => B): F[A] => F[B] =
          covariant.map(f)
      }
  }

  def spec = suite("LawsFSpec")(
    suite("covariantLaws")(
      testM("option") {
        checkAllLaws(Covariant)(OptionGenF, Gen.anyInt)
      }
    )
  )
}

package zio.test.laws

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

  object EqualF {

    def apply[F[_]](implicit equalF: EqualF[F]): EqualF[F] =
      equalF

    implicit val OptionEqualF: EqualF[Option] =
      new EqualF[Option] {
        def deriveEqual[A](A: Equal[A]): Equal[Option[A]] = { (l, r) =>
          (l, r) match {
            case (None, None)       => true
            case (Some(l), Some(r)) => A.equal(l, r)
            case _                  => false
          }
        }
      }
  }

  trait Covariant[F[+_]] {
    def map[A, B](f: A => B): F[A] => F[B]
  }

  object Covariant extends LawfulF.Covariant[CovariantEqualF, Equal] {

    def apply[F[+_]](implicit covariant: Covariant[F]): Covariant[F] =
      covariant

    val identityLaw: LawsF.Covariant[CovariantEqualF, Equal] =
      new LawsF.Covariant.Law1[CovariantEqualF, Equal]("identityLaw") {
        def apply[F[+_]: CovariantEqualF, A: Equal](fa: F[A]): TestResult =
          fa.map(identity) <-> fa
      }

    val compositionLaw: LawsF.Covariant[CovariantEqualF, Equal] =
      new ZLawsF.Covariant.ComposeLaw[CovariantEqualF, Equal]("compositionLaw") {
        def apply[F[+_]: CovariantEqualF, A: Equal, B: Equal, C: Equal](fa: F[A], f: A => B, g: B => C): TestResult =
          fa.map(f).map(g) <-> fa.map(f andThen g)
      }

    val laws: LawsF.Covariant[CovariantEqualF, Equal] =
      identityLaw + compositionLaw

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

  trait CovariantEqualF[F[+_]] extends Covariant[F] with EqualF[F]

  object CovariantEqualF {

    implicit def derive[F[+_]](implicit covariant: Covariant[F], equalF: EqualF[F]): CovariantEqualF[F] =
      new CovariantEqualF[F] {
        def deriveEqual[A](equal: Equal[A]): Equal[F[A]] =
          equalF.deriveEqual(equal)
        def map[A, B](f: A => B): F[A] => F[B] =
          covariant.map(f)
      }
  }

  def spec = suite("LawsFSpec")(
    suite("covariantLaws")(
      testM("option") {
        checkAllLaws(Covariant)(GenF.option, Gen.anyInt)
      }
    )
  )
}

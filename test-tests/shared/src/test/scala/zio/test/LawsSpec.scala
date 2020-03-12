package zio.test

import zio.ZIO
import zio.test.Assertion._

object LawsSpec extends ZIOBaseSpec {

  implicit class BoolSyntax(l: Boolean) {
    def ==>(r: Boolean): Boolean = r || !l
  }

  trait Equal[-A] {
    def equal(a1: A, a2: A): Boolean
  }

  object Equal {
    implicit val intEqual: Equal[Int]       = _ == _
    implicit val stringEqual: Equal[String] = _ == _
  }

  implicit class EqualSyntax[A](self: A) {
    def ===(that: A)(implicit eq: Equal[A]): Boolean =
      eq.equal(self, that)
  }

  val reflexiveLaw = new Laws.Law[Equal]("reflexiveLaw") {
    def apply[A](caps: Equal[A], a1: A, a2: A, a3: A) = {
      implicit val eq: Equal[A] = caps
      assert(a1 === a1)(isTrue)
    }
  }

  val symmetryLaw = new Laws.Law[Equal]("symmetryLaw") {
    def apply[A](caps: Equal[A], a1: A, a2: A, a3: A) = {
      implicit val eq: Equal[A] = caps
      assert((a1 === a2) ==> (a2 === a1))(isTrue)
    }
  }

  val transitivityLaw = new Laws.Law[Equal]("transitivityLaw") {
    def apply[A](caps: Equal[A], a1: A, a2: A, a3: A) = {
      implicit val eq: Equal[A] = caps
      assert(((a1 === a2) && (a2 === a3)) ==> (a1 === a3))(isTrue)
    }
  }

  val equalLaws = reflexiveLaw + symmetryLaw + transitivityLaw

  def checkAllLaws[Caps[_], R, R1 <: R, A](
    laws: Laws[Caps, R]
  )(gen: Gen[R1, A])(implicit caps: Caps[A]): ZIO[R1, Nothing, TestResult] =
    laws.run(caps, gen)

  def spec =
    suite("LawsSpec") {
      suite("equalLaws")(
        testM("int") {
          checkAllLaws(equalLaws)(Gen.anyInt)
        },
        testM("string") {
          checkAllLaws(equalLaws)(Gen.anyString)
        }
      )
    }
}

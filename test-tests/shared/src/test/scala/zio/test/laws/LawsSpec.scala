package zio.test.laws

import zio.test._

object LawsSpec extends ZIOBaseSpec {

  def equalTo[A: Equal](expected: A): Assertion[A] =
    Assertion.assertion("equalTo")(Assertion.Render.param(expected))(_ === expected)

  implicit class AssertEqualToSyntax[A](private val self: A) extends AnyVal {
    def <->(that: A)(implicit eq: Equal[A]): TestResult =
      assert(self)(equalTo(that))
  }

  trait Equal[-A] {
    def equal(a1: A, a2: A): Boolean
  }

  object Equal extends Lawful[Equal] {

    val reflexiveLaw: Laws.Law1[Equal] = new Laws.Law1[Equal]("reflexiveLaw") {
      def apply[A: Equal](a1: A) =
        a1 <-> a1
    }

    val symmetryLaw: Laws.Law2[Equal] = new Laws.Law2[Equal]("symmetryLaw") {
      def apply[A: Equal](a1: A, a2: A) =
        (a1 <-> a2) <==> (a2 <-> a1)
    }

    val transitivityLaw: Laws.Law3[Equal] = new Laws.Law3[Equal]("transitivityLaw") {
      def apply[A: Equal](a1: A, a2: A, a3: A) =
        ((a1 <-> a2) && (a2 <-> a3)) ==> (a1 <-> a3)
    }

    val laws: ZLaws[Equal, Any] = reflexiveLaw + symmetryLaw + transitivityLaw

    implicit val booleanEqual: Equal[Boolean] = _ == _
    implicit val intEqual: Equal[Int]         = _ == _
    implicit val stringEqual: Equal[String]   = _ == _
  }

  implicit class EqualSyntax[A](private val self: A) extends AnyVal {
    def ===(that: A)(implicit eq: Equal[A]): Boolean =
      eq.equal(self, that)
  }

  def spec: ZSpec[Environment, Failure] =
    suite("LawsSpec") {
      suite("equalLaws")(
        testM("int") {
          checkAllLaws(Equal)(Gen.anyInt)
        },
        testM("string") {
          checkAllLaws(Equal)(Gen.anyString)
        }
      )
    }
}

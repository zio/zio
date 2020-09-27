package zio.test.laws

import zio.test._

object Laws2Spec extends ZIOBaseSpec {

  type AnyF[_] = Any

  def equalTo[A: Equal](expected: A): Assertion[A] =
    Assertion.assertion("equalTo")(Assertion.Render.param(expected))(_ === expected)

  implicit class AssertEqualToSyntax[A](private val self: A) extends AnyVal {
    def <->(that: A)(implicit eq: Equal[A]): TestResult =
      assert(self)(equalTo(that))
  }

  trait Equal[-A] {
    def equal(a1: A, a2: A): Boolean
  }

  object Equal {
    implicit val byteEqual: Equal[Byte] = _ == _
    implicit val charEqual: Equal[Char] = _ == _
    implicit def listEqual[A: Equal]: Equal[List[A]] =
      (l, r) => l.corresponds(r)(_ === _)
    implicit val stringEqual: Equal[String] = _ == _
    implicit def vectorEqual[A: Equal]: Equal[Vector[A]] =
      (l, r) => l.corresponds(r)(_ === _)
  }

  implicit class EqualSyntax[A](private val self: A) extends AnyVal {
    def ===(that: A)(implicit eq: Equal[A]): Boolean =
      eq.equal(self, that)
  }

  final case class Equivalence[A, B](to: A => B, from: B => A)

  object Equivalence extends Lawful2[Equivalence, Equal, Equal] {

    val leftIdentity: Laws2.Law1Left[Equivalence, Equal, AnyF] =
      new Laws2.Law1Left[Equivalence, Equal, AnyF]("leftIdentity") {
        def apply[A: Equal, B: AnyF](a1: A)(implicit Equivalence: Equivalence[A, B]): TestResult =
          Equivalence.from(Equivalence.to(a1)) <-> a1
      }

    val rightIdentity: Laws2.Law1Right[Equivalence, AnyF, Equal] =
      new Laws2.Law1Right[Equivalence, AnyF, Equal]("rightIdentity") {
        def apply[A: AnyF, B: Equal](b1: B)(implicit Equivalence: Equivalence[A, B]): TestResult =
          Equivalence.to(Equivalence.from(b1)) <-> b1
      }

    val laws: ZLaws2[Equivalence, Equal, Equal, Any] = leftIdentity + rightIdentity

    val byteListByteVectorEquivalence: Equivalence[List[Byte], Vector[Byte]] =
      Equivalence(_.toVector, _.toList)

    val charListStringEquivalence: Equivalence[List[Char], String] =
      Equivalence(_.mkString, _.toList)
  }

  def spec: ZSpec[Environment, Failure] =
    suite("Laws2Spec") {
      suite("equivalenceLaws")(
        testM("byteList <=> byteVector") {
          val genByteList          = Gen.listOf(Gen.anyByte)
          val genByteVector        = Gen.vectorOf(Gen.anyByte)
          implicit val equivalence = Equivalence.byteListByteVectorEquivalence
          checkAllLaws(Equivalence)(genByteList, genByteVector)
        },
        testM("charList <=> String") {
          val genCharList          = Gen.listOf(Gen.anyChar)
          val genString            = Gen.anyString
          implicit val equivalence = Equivalence.charListStringEquivalence
          checkAllLaws(Equivalence)(genCharList, genString)
        }
      )
    }
}

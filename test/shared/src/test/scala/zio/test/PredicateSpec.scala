package zio.test

import zio.Exit
import zio.test.Predicate._

import scala.Predef.{ assert => SAssert }

object PredicateSpec {

  private def test(assertion: Boolean, message: String): Unit =
    SAssert(assertion, s"PredicateTest: $message")

  private def testSuccess(testResult: TestResult, message: String): Unit =
    test(testResult.success, message)

  private def testFailure(testResult: TestResult, message: String): Unit =
    test(testResult.failure, message)

  private case class SampleUser(name: String, age: Int)
  private val sampleUser = SampleUser("User", 42)

  def run(): Unit = {

    testSuccess(assert(42, anything), message = "anything must always succeeds")

    testSuccess(
      assert(Seq("zio", "scala"), contains("zio")),
      message = "contains must succeed when iterable contains specified element"
    )

    testFailure(
      assert(Seq("zio", "scala"), contains("java")),
      message = "contains must fail when iterable does not contain specified element"
    )

    testSuccess(
      assert(42, Predicate.equals(42)),
      message = "equals must succeed when value equals specified value"
    )

    testFailure(
      assert(0, Predicate.equals(42)),
      message = "equals must fail when value does not equal specified value"
    )

    testSuccess(
      assert(Seq(1, 42, 5), exists(Predicate.equals(42))),
      message = "exists must succeed when at least one element of iterable satisfy specified predicate"
    )

    testFailure(
      assert(Seq(1, 42, 5), exists(Predicate.equals(0))),
      message = "exists must fail when all elements of iterable do not satisfy specified predicate"
    )

    testSuccess(
      assert(Exit.fail("Some Error"), fails(Predicate.equals("Some Error"))),
      message = "fails must succeed when error value satisfy specified predicate"
    )

    testFailure(
      assert(Exit.fail("Other Error"), fails(Predicate.equals("Some Error"))),
      message = "fails must fail when error value does not satisfy specified predicate"
    )

    testSuccess(
      assert(SampleUser("User", 23), hasField[SampleUser, Int]("age", _.age, isWithin(0, 99))),
      message = "hasField must succeed when field value satisfy specified predicate"
    )

    testSuccess(
      assert(Seq("a", "bb", "ccc"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3)))),
      message = "forall must succeed when all elements of iterable satisfy specified predicate"
    )

    testFailure(
      assert(Seq("a", "bb", "dddd"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3)))),
      message = "forall must fail when one element of iterable do not satisfy specified predicate"
    )

    testSuccess(
      assert(Seq(1, 2, 3), hasSize(Predicate.equals(3))),
      message = "hasSize must succeed when iterable size is equal to specified predicate"
    )

    testSuccess(
      assert(0, isGreaterThan(42)),
      message = "isGreaterThan must succeed when specified value is greater than supplied value"
    )

    testFailure(
      assert(42, isGreaterThan(42)),
      message = "isGreaterThan must fail when specified value is less than or equal supplied value"
    )

    testSuccess(
      assert(42, isGreaterThanEqual(42)),
      message = "isGreaterThanEqual must succeed when specified value is greater than or equal supplied value"
    )

    testSuccess(
      assert(
        sampleUser,
        isCase[SampleUser, (String, Int)](
          termName = "SampleUser",
          SampleUser.unapply,
          Predicate.equals((sampleUser.name, sampleUser.age))
        )
      ),
      message = "isCase must succeed when unapplied Proj satisfy specified predicate"
    )

    testFailure(
      assert(42, isCase[Int, String](termName = "term", _ => None, Predicate.equals("number: 42"))),
      message = "isCase must fail when unapply fails (returns None)"
    )

    testSuccess(
      assert(true, isTrue),
      message = "isTrue must succeed when supplied value is true"
    )

    testSuccess(
      assert(false, isFalse),
      message = "isFalse must succeed when supplied value is false"
    )

    testSuccess(
      assert(Left(42), isLeft(Predicate.equals(42))),
      message = "isLeft must succeed when supplied value is Left and satisfy specified predicate"
    )

    testSuccess(assert(42, isLessThan(0)), message = "lt must succeed when specified value is less than supplied value")

    testFailure(
      assert(42, isLessThan(42)),
      message = "isLessThan must fail when specified value is greater than or equal supplied value"
    )

    testSuccess(
      assert(42, isGreaterThanEqual(42)),
      message = "isGreaterThanEqual must succeed when specified value is less than or equal supplied value"
    )

    testSuccess(
      assert(None, isNone),
      message = "isNone must succeed when specified value is None"
    )

    testFailure(
      assert(Some(42), isNone),
      message = "isNone must fail when specified value is not None"
    )

    testSuccess(
      assert(0, not(Predicate.equals(42))),
      message = "not must succeed when negation of specified predicate is true"
    )

    testFailure(
      assert(42, nothing),
      message = "nothing must always fail"
    )

    testSuccess(
      assert(Right(42), isRight(Predicate.equals(42))),
      message = "right must succeed when supplied value is Right and satisfy specified predicate"
    )

    testSuccess(
      assert(Some("zio"), isSome(Predicate.equals("zio"))),
      message = "isSome must succeed when supplied value is Some and satisfy specified predicate"
    )

    testFailure(
      assert(None, isSome(Predicate.equals("zio"))),
      message = "isSome must fail when supplied value is None"
    )

    testSuccess(
      assert(Exit.succeed("Some Error"), succeeds(Predicate.equals("Some Error"))),
      message = "succeeds must succeed when supplied value is Exit.succeed and satisfy specified predicate"
    )

    testFailure(
      assert(Exit.fail("Some Error"), succeeds(Predicate.equals("Some Error"))),
      message = "succeeds must fail when supplied value is Exit.fail"
    )

    testSuccess(
      assert(10, isWithin(0, 10)),
      message = "isWithin must succeed when supplied value is within range (inclusive)"
    )

    testFailure(
      assert(42, isWithin(0, 10)),
      message = "isWithin must fail when supplied value is out of range"
    )

    val nameStartsWithA  = hasField[SampleUser, Boolean]("name", _.name.startsWith("A"), isTrue)
    val nameStartsWithU  = hasField[SampleUser, Boolean]("name", _.name.startsWith("U"), isTrue)
    val ageLessThen20    = hasField[SampleUser, Int]("age", _.age, isLessThan(20))
    val ageGreaterThen20 = hasField[SampleUser, Int]("age", _.age, isGreaterThan(20))

    testSuccess(
      assert(sampleUser, nameStartsWithU && ageLessThen20),
      message = "and must succeed when both predicates are satisfied"
    )

    testFailure(
      assert(sampleUser, nameStartsWithA && ageLessThen20),
      message = "and must fail when one of predicates is not satisfied"
    )

    testSuccess(
      assert(sampleUser, (nameStartsWithA || nameStartsWithU) && ageLessThen20),
      message = "or must succeed when one of predicates is satisfied"
    )

    testFailure(
      assert(sampleUser, nameStartsWithA || ageGreaterThen20),
      message = "or must fail when both predicates are not satisfied"
    )

    testSuccess(
      assert(sampleUser, nameStartsWithA.negate),
      message = "negate must succeed when negation of predicate is true"
    )

    test(nameStartsWithU.test(sampleUser), message = "test must return true when given element satisfy predicate")

    test(
      !nameStartsWithA.test(sampleUser),
      message = "test must return false when given element does not satisfy predicate"
    )
  }

}

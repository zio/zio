package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.Exit
import zio.test.Predicate._
import zio.test.TestUtils.label

object PredicateSpec {

  private def test(assertion: Boolean, message: String)(implicit ec: ExecutionContext): Future[(Boolean, String)] =
    label(Future.successful(assertion), s"PredicateTest: $message")

  private def testSuccess(testResult: TestResult, message: String)(
    implicit ec: ExecutionContext
  ): Future[(Boolean, String)] =
    label(Future.successful(testResult.success), message)

  private def testFailure(testResult: TestResult, message: String)(
    implicit ec: ExecutionContext
  ): Future[(Boolean, String)] =
    label(Future.successful(testResult.failure), message)

  case class SampleUser(name: String, age: Int)
  val sampleUser = SampleUser("User", 42)

  val nameStartsWithA  = hasField[SampleUser, Boolean]("name", _.name.startsWith("A"), isTrue)
  val nameStartsWithU  = hasField[SampleUser, Boolean]("name", _.name.startsWith("U"), isTrue)
  val ageLessThen20    = hasField[SampleUser, Int]("age", _.age, lessThan(20))
  val ageGreaterThen20 = hasField[SampleUser, Int]("age", _.age, greaterThan(20))

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    testSuccess(assert(42, anything), message = "anything must always succeeds"),
    testSuccess(
      assert(Seq("zio", "scala"), contains("zio")),
      message = "contains must succeed when iterable contains specified element"
    ),
    testFailure(
      assert(Seq("zio", "scala"), contains("java")),
      message = "contains must fail when iterable does not contain specified element"
    ),
    testSuccess(
      assert(Seq(1, 42, 5), exists(equalTo(42))),
      message = "exists must succeed when at least one element of iterable satisfy specified predicate"
    ),
    testFailure(
      assert(Seq(1, 42, 5), exists(equalTo(0))),
      message = "exists must fail when all elements of iterable do not satisfy specified predicate"
    ),
    testSuccess(
      assert(Exit.fail("Some Error"), fails(equalTo("Some Error"))),
      message = "fails must succeed when error value satisfy specified predicate"
    ),
    testFailure(
      assert(Exit.fail("Other Error"), fails(equalTo("Some Error"))),
      message = "fails must fail when error value does not satisfy specified predicate"
    ),
    testSuccess(
      assert(SampleUser("User", 23), hasField[SampleUser, Int]("age", _.age, isWithin(0, 99))),
      message = "hasField must succeed when field value satisfy specified predicate"
    ),
    testSuccess(
      assert(Seq("a", "bb", "ccc"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3)))),
      message = "forall must succeed when all elements of iterable satisfy specified predicate"
    ),
    testFailure(
      assert(Seq("a", "bb", "dddd"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3)))),
      message = "forall must fail when one element of iterable do not satisfy specified predicate"
    ),
    testSuccess(
      assert(Seq(1, 2, 3), hasSize(equalTo(3))),
      message = "hasSize must succeed when iterable size is equal to specified predicate"
    ),
    testFailure(
      assert(42, isCase[Int, String](termName = "term", _ => None, equalTo("number: 42"))),
      message = "isCase must fail when unapply fails (returns None)"
    ),
    testSuccess(
      assert(
        sampleUser,
        isCase[SampleUser, (String, Int)](
          termName = "SampleUser",
          SampleUser.unapply,
          equalTo((sampleUser.name, sampleUser.age))
        )
      ),
      message = "isCase must succeed when unapplied Proj satisfy specified predicate"
    ),
    testSuccess(
      assert(42, equalTo(42)),
      message = "equalTo must succeed when value equals specified value"
    ),
    testFailure(
      assert(0, equalTo(42)),
      message = "equalTo must fail when value does not equal specified value"
    ),
    testSuccess(
      assert(0, greaterThan(42)),
      message = "greaterThan must succeed when specified value is greater than supplied value"
    ),
    testFailure(
      assert(42, greaterThan(42)),
      message = "greaterThan must fail when specified value is less than or equal supplied value"
    ),
    testSuccess(
      assert(42, greaterThanEqualTo(42)),
      message = "greaterThanEqualTo must succeed when specified value is greater than or equal supplied value"
    ),
    testSuccess(
      assert(false, isFalse),
      message = "isFalse must succeed when supplied value is false"
    ),
    testSuccess(
      assert(Left(42), isLeft(equalTo(42))),
      message = "isLeft must succeed when supplied value is Left and satisfy specified predicate"
    ),
    testSuccess(
      assert(None, isNone),
      message = "isNone must succeed when specified value is None"
    ),
    testFailure(
      assert(Some(42), isNone),
      message = "isNone must fail when specified value is not None"
    ),
    testSuccess(
      assert(Right(42), isRight(equalTo(42))),
      message = "isRight must succeed when supplied value is Right and satisfy specified predicate"
    ),
    testSuccess(
      assert(Some("zio"), isSome(equalTo("zio"))),
      message = "isSome must succeed when supplied value is Some and satisfy specified predicate"
    ),
    testFailure(
      assert(None, isSome(equalTo("zio"))),
      message = "isSome must fail when supplied value is None"
    ),
    testSuccess(
      assert(true, isTrue),
      message = "isTrue must succeed when supplied value is true"
    ),
    testSuccess(
      assert((), isUnit),
      message = "isUnit must succeed when supplied value is ()"
    ),
    testFailure(
      assert(10, isUnit),
      message = "isUnit must fail when supplied value is not ()"
    ),
    testSuccess(
      assert(10, isWithin(0, 10)),
      message = "isWithin must succeed when supplied value is within range (inclusive)"
    ),
    testFailure(
      assert(42, isWithin(0, 10)),
      message = "isWithin must fail when supplied value is out of range"
    ),
    testSuccess(
      assert(42, lessThan(0)),
      message = "lessThan must succeed when specified value is less than supplied value"
    ),
    testFailure(
      assert(42, lessThan(42)),
      message = "lessThan must fail when specified value is greater than or equal supplied value"
    ),
    testSuccess(
      assert(42, lessThanEqualTo(42)),
      message = "lessThanEqualTo must succeed when specified value is less than or equal supplied value"
    ),
    testSuccess(
      assert(0, not(equalTo(42))),
      message = "not must succeed when negation of specified predicate is true"
    ),
    testFailure(
      assert(42, nothing),
      message = "nothing must always fail"
    ),
    testSuccess(
      assert(Right(42), isRight(equalTo(42))),
      message = "isRight must succeed when supplied value is Right and satisfy specified predicate"
    ),
    testSuccess(
      assert(Exit.succeed("Some Error"), succeeds(equalTo("Some Error"))),
      message = "succeeds must succeed when supplied value is Exit.succeed and satisfy specified predicate"
    ),
    testFailure(
      assert(Exit.fail("Some Error"), succeeds(equalTo("Some Error"))),
      message = "succeeds must fail when supplied value is Exit.fail"
    ),
    testSuccess(
      assert(sampleUser, nameStartsWithU && ageLessThen20),
      message = "and must succeed when both predicates are satisfied"
    ),
    testFailure(
      assert(sampleUser, nameStartsWithA && ageLessThen20),
      message = "and must fail when one of predicates is not satisfied"
    ),
    testSuccess(
      assert(sampleUser, (nameStartsWithA || nameStartsWithU) && ageLessThen20),
      message = "or must succeed when one of predicates is satisfied"
    ),
    testFailure(
      assert(sampleUser, nameStartsWithA || ageGreaterThen20),
      message = "or must fail when both predicates are not satisfied"
    ),
    testSuccess(
      assert(sampleUser, nameStartsWithA.negate),
      message = "negate must succeed when negation of predicate is true"
    ),
    test(nameStartsWithU.test(sampleUser), message = "test must return true when given element satisfy predicate"),
    test(
      !nameStartsWithA.test(sampleUser),
      message = "test must return false when given element does not satisfy predicate"
    )
  )
}

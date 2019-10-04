package zio.test

import scala.concurrent.Future

import zio.Exit
import zio.test.Assertion._
import zio.test.TestUtils.label

object AssertionSpec extends ZIOBaseSpec {

  private def test(assertion: Boolean, message: String): Async[(Boolean, String)] =
    label(Future.successful(assertion), s"AssertionTest: $message")

  private def testSuccess(testResult: TestResult, message: String): Async[(Boolean, String)] =
    label(Future.successful(testResult.isSuccess), message)

  private def testFailure(testResult: TestResult, message: String): Async[(Boolean, String)] =
    label(Future.successful(testResult.isFailure), message)

  case class SampleUser(name: String, age: Int)
  val sampleUser      = SampleUser("User", 42)
  val sampleException = new Exception

  val nameStartsWithA  = hasField[SampleUser, Boolean]("name", _.name.startsWith("A"), isTrue)
  val nameStartsWithU  = hasField[SampleUser, Boolean]("name", _.name.startsWith("U"), isTrue)
  val ageLessThan20    = hasField[SampleUser, Int]("age", _.age, isLessThan(20))
  val ageGreaterThan20 = hasField[SampleUser, Int]("age", _.age, isGreaterThan(20))

  val someException = new RuntimeException("Boom!")

  def run: List[Async[(Boolean, String)]] = List(
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
      assert(42, equalTo(42)),
      message = "equalTo must succeed when value equals specified value"
    ),
    testFailure(
      assert(0, equalTo(42)),
      message = "equalTo must fail when value does not equal specified value"
    ),
    testSuccess(
      assert(Array(1, 2, 3), equalTo(Array(1, 2, 3))),
      message = "equalTo must succeed when array equals specified array"
    ),
    testFailure(
      assert(Array(1, 2, 3), equalTo(Array(1, 2, 4))),
      message = "equalTo must fail when array does not equal specified array"
    ),
    testSuccess(
      assert(Seq(1, 42, 5), exists(equalTo(42))),
      message = "exists must succeed when at least one element of iterable satisfy specified assertion"
    ),
    testFailure(
      assert(Seq(1, 42, 5), exists(equalTo(0))),
      message = "exists must fail when all elements of iterable do not satisfy specified assertion"
    ),
    testSuccess(
      assert(Exit.fail("Some Error"), fails(equalTo("Some Error"))),
      message = "fails must succeed when error value satisfy specified assertion"
    ),
    testFailure(
      assert(Exit.fail("Other Error"), fails(equalTo("Some Error"))),
      message = "fails must fail when error value does not satisfy specified assertion"
    ),
    testSuccess(
      assert(Exit.die(someException), dies(equalTo(someException))),
      message = "dies must succeed when exception satisfy specified assertion"
    ),
    testFailure(
      assert(Exit.die(new RuntimeException("Bam!")), dies(equalTo(someException))),
      message = "dies must fail when exception does not satisfy specified assertion"
    ),
    testSuccess(
      assert(Seq("a", "bb", "ccc"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3)))),
      message = "forall must succeed when all elements of iterable satisfy specified assertion"
    ),
    testFailure(
      assert(Seq("a", "bb", "dddd"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3)))),
      message = "forall must fail when one element of iterable do not satisfy specified assertion"
    ),
    testSuccess(
      assert(SampleUser("User", 23), hasField[SampleUser, Int]("age", _.age, isWithin(0, 99))),
      message = "hasField must succeed when field value satisfy specified assertion"
    ),
    testSuccess(
      assert(Seq(1, 2, 3), hasSize(equalTo(3))),
      message = "hasSize must succeed when iterable size is equal to specified assertion"
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
      message = "isCase must succeed when unapplied Proj satisfy specified assertion"
    ),
    testSuccess(
      assert(false, isFalse),
      message = "isFalse must succeed when supplied value is false"
    ),
    testSuccess(
      assert(42, isGreaterThan(0)),
      message = "isGreaterThan must succeed when specified value is greater than supplied value"
    ),
    testFailure(
      assert(42, isGreaterThan(42)),
      message = "isGreaterThan must fail when specified value is less than or equal supplied value"
    ),
    testSuccess(
      assert(42, isGreaterThanEqualTo(42)),
      message = "greaterThanEqualTo must succeed when specified value is greater than or equal supplied value"
    ),
    testSuccess(
      assert(Left(42), isLeft(equalTo(42))),
      message = "isLeft must succeed when supplied value is Left and satisfy specified assertion"
    ),
    testSuccess(
      assert(0, isLessThan(42)),
      message = "isLessThan must succeed when specified value is less than supplied value"
    ),
    testFailure(
      assert(42, isLessThan(42)),
      message = "isLessThan must fail when specified value is greater than or equal supplied value"
    ),
    testSuccess(
      assert(42, isLessThanEqualTo(42)),
      message = "isLessThanEqualTo must succeed when specified value is less than or equal supplied value"
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
      message = "isRight must succeed when supplied value is Right and satisfy specified assertion"
    ),
    testSuccess(
      assert(Some("zio"), isSome(equalTo("zio"))),
      message = "isSome must succeed when supplied value is Some and satisfy specified assertion"
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
      assert(0, not(equalTo(42))),
      message = "not must succeed when negation of specified assertion is true"
    ),
    testFailure(
      assert(42, nothing),
      message = "nothing must always fail"
    ),
    testSuccess(
      assert(Exit.succeed("Some Error"), succeeds(equalTo("Some Error"))),
      message = "succeeds must succeed when supplied value is Exit.succeed and satisfy specified assertion"
    ),
    testFailure(
      assert(Exit.fail("Some Error"), succeeds(equalTo("Some Error"))),
      message = "succeeds must fail when supplied value is Exit.fail"
    ),
    testSuccess(
      assert(throw sampleException, throws(equalTo(sampleException))),
      message = "throws must succeed when given assertion is correct"
    ),
    testSuccess(
      assert(sampleUser, nameStartsWithU && ageGreaterThan20),
      message = "and must succeed when both assertions are satisfied"
    ),
    testFailure(
      assert(sampleUser, nameStartsWithA && ageGreaterThan20),
      message = "and must fail when one of assertions is not satisfied"
    ),
    testSuccess(
      assert(sampleUser, (nameStartsWithA || nameStartsWithU) && ageGreaterThan20),
      message = "or must succeed when one of assertions is satisfied"
    ),
    testFailure(
      assert(sampleUser, nameStartsWithA || ageLessThan20),
      message = "or must fail when both assertions are not satisfied"
    ),
    testSuccess(
      assert(sampleUser, nameStartsWithA.negate),
      message = "negate must succeed when negation of assertion is true"
    ),
    test(nameStartsWithU.test(sampleUser), message = "test must return true when given element satisfy assertion"),
    test(
      !nameStartsWithA.test(sampleUser),
      message = "test must return false when given element does not satisfy assertion"
    ),
    testSuccess(
      assert("this is a value", containsString("is a")),
      message = "containsString must succeed when string is found"
    ),
    testFailure(
      assert("this is a value", containsString("_NOTHING_")),
      message = "containsString must return false when the string is not contained"
    ),
    testSuccess(
      assert("", isEmptyString),
      message = "isEmptyString must succeed when the string is empty"
    ),
    testFailure(
      assert("some string", isEmptyString),
      message = "isEmptyString must fail when the string is not empty"
    ),
    testSuccess(
      assert("some string", isNonEmptyString),
      message = "isNonEmptyString must succeed when the string is not empty"
    ),
    testFailure(
      assert("", isNonEmptyString),
      message = "isNonEmptyString must fail when the string is empty"
    ),
    testSuccess(
      assert("Some String", equalsIgnoreCase("some string")),
      message = "equalsIgnoreCase must succeed when the supplied value matches"
    ),
    testFailure(
      assert("Some Other String", equalsIgnoreCase("some string")),
      message = "equalsIgnoreCase must fail when the supplied value does not match"
    ),
    testSuccess(
      assert("zio", startsWith("z")),
      message = "startsWith must succeed when the supplied value starts with the specified string"
    ),
    testFailure(
      assert("zio", startsWith("o")),
      message = "startsWith must fail when the supplied value does not start with the specified string"
    ),
    testSuccess(
      assert("zio", endsWith("o")),
      message = "endsWith must succeed when the supplied value ends with the specified string"
    ),
    testFailure(
      assert("zio", endsWith("z")),
      message = "endsWith must fail when the supplied value does not end with the specified string"
    ),
    testSuccess(
      assert("(123) 456-7890", matchesRegex("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$")),
      message = "matches must succeed when the string matches the regex"
    ),
    testFailure(
      assert("456-7890", matchesRegex("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$")),
      message = "matches must fail when the string does not match the regex"
    ),
    testSuccess(
      assert(5.5, approximatelyEquals(5.0, 3.0)),
      message = "approximatelyEquals must succeed when number is within range"
    ),
    testFailure(
      assert(50.0, approximatelyEquals(5.0, 3.0)),
      message = "approximatelyEquals must fail when number is not within range"
    ),
    testSuccess(
      assert(Seq(), isEmpty),
      message = "isEmpty must succeed when the traversable is empty"
    ),
    testFailure(
      assert(Seq(1, 2, 3), isEmpty),
      message = "isEmpty must fail when the traversable is not empty"
    ),
    testSuccess(
      assert(Seq(1, 2, 3), isNonEmpty),
      message = "isNonEmpty must succeed when the traversable is not empty"
    ),
    testFailure(
      assert(Seq(), isNonEmpty),
      message = "isNonEmpty must fail when the traversable is empty"
    ),
    testSuccess(
      assert(Seq(1, 2, 3), hasSameElements(Seq(1, 2, 3))),
      message = "containsTheSameElements must succeed when both iterables contain the same elements"
    ),
    testFailure(
      assert(Seq(1, 2, 3, 4), hasSameElements(Seq(1, 2, 3))),
      message = "containsTheSameElements must fail when the iterables do not contain the same elements"
    ),
    testSuccess(
      assert(Seq(4, 3, 1, 2), hasSameElements(Seq(1, 2, 3, 4))),
      message = "containsTheSameElements must succeed when both iterables contain the same elements in different order"
    ),
    testFailure(
      assert(
        Seq("a", "a", "b", "b", "b", "c", "c", "c", "c", "c"),
        hasSameElements(Seq("a", "a", "a", "a", "a", "b", "b", "c", "c", "c"))
      ),
      message =
        "hasSameElements must fail when both iterables have the same size, have the same values but they appear a different number of times."
    )
  )
}

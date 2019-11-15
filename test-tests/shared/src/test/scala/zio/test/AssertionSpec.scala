package zio.test

import zio.Exit
import zio.test.Assertion._
import zio.test.BoolAlgebra.Value
import zio.test.TestAspect._

object AssertionSpec extends ZIOBaseSpec {

  def spec = suite("AssertionSpec")(
    test("anything must always succeeds") {
      assert(42, anything)
    },
    test("contains must succeed when iterable contains specified element") {
      assert(Seq("zio", "scala"), contains("zio"))
    },
    test("contains must fail when iterable does not contain specified element") {
      assert(Seq("zio", "scala"), contains("java"))
    } @@ failure,
    test("equalTo must succeed when value equals specified value") {
      assert(42, equalTo(42))
    },
    test("equalTo must fail when value does not equal specified value") {
      assert(0, equalTo(42))
    } @@ failure,
    test("equalTo must succeed when array equals specified array") {
      assert(Array(1, 2, 3), equalTo(Array(1, 2, 3)))
    },
    test("equalTo must fail when array does not equal specified array") {
      assert(Array(1, 2, 3), equalTo(Array(1, 2, 4)))
    } @@ failure,
    test("equalTo must not have type inference issues") {
      assert(List(1, 2, 3).filter(_ => false), equalTo(List.empty))
    },
    test("equalTo must fail when comparing two unrelated types") {
      assert(1, equalTo("abc"))
    } @@ failure,
    test("exists must succeed when at least one element of iterable satisfy specified assertion") {
      assert(Seq(1, 42, 5), exists(equalTo(42)))
    },
    test("exists must fail when all elements of iterable do not satisfy specified assertion") {
      assert(Seq(1, 42, 5), exists(equalTo(0)))
    } @@ failure,
    test("exists must fail when iterable is empty") {
      assert(Seq(), exists(hasField[String, Int]("length", _.length, isWithin(0, 3))))
    } @@ failure,
    test("fails must succeed when error value satisfy specified assertion") {
      assert(Exit.fail("Some Error"), fails(equalTo("Some Error")))
    },
    test("fails must fail when error value does not satisfy specified assertion") {
      assert(Exit.fail("Other Error"), fails(equalTo("Some Error")))
    } @@ failure,
    test("dies must succeed when exception satisfy specified assertion") {
      assert(Exit.die(someException), dies(equalTo(someException)))
    },
    test("dies must fail when exception does not satisfy specified assertion") {
      assert(Exit.die(new RuntimeException("Bam!")), dies(equalTo(someException)))
    } @@ failure,
    test("forall must succeed when all elements of iterable satisfy specified assertion") {
      assert(Seq("a", "bb", "ccc"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3))))
    },
    test("forall must fail when one element of iterable do not satisfy specified assertion") {
      assert(Seq("a", "bb", "dddd"), forall(hasField[String, Int]("length", _.length, isWithin(0, 3))))
    } @@ failure,
    test("forall must succeed when an iterable is empty") {
      assert(Seq(), forall(hasField[String, Int]("length", _.length, isWithin(0, 3))))
    },
    test("hasField must succeed when field value satisfy specified assertion") {
      assert(SampleUser("User", 23), hasField[SampleUser, Int]("age", _.age, isWithin(0, 99)))
    },
    test("hasSize must succeed when iterable size is equal to specified assertion") {
      assert(Seq(1, 2, 3), hasSize(equalTo(3)))
    },
    test("hasFirst must fail when an iterable is empty") {
      assert(Seq(), hasFirst(anything))
    } @@ failure,
    test("hasFirst must succeed when a head is equal to a specific assertion") {
      assert(Seq(1, 2, 3), hasFirst(equalTo(1)))
    },
    test("hasFirst must fail when a head is not equal to a specific assertion") {
      assert(Seq(1, 2, 3), hasFirst(equalTo(100)))
    } @@ failure,
    test("hasLast must fail when an iterable is empty") {
      assert(Seq(), hasLast(anything))
    } @@ failure,
    test("hasLast must succeed when a last is equal to a specific assertion") {
      assert(Seq(1, 2, 3), hasLast(equalTo(3)))
    },
    test("hasLast must fail when a last is not equal to specific assertion") {
      assert(Seq(1, 2, 3), hasLast(equalTo(100)))
    } @@ failure,
    test("hasAt must fail when an index is outside of a sequence range") {
      assert(Seq(1, 2, 3), hasAt(-1)(anything))
    } @@ failure,
    test("hasAt must fail when an index is outside of a sequence range 2") {
      assert(Seq(1, 2, 3), hasAt(3)(anything))
    } @@ failure,
    test("hasAt must fail when a value is not equal to a specific assertion") {
      assert(Seq(1, 2, 3), hasAt(1)(equalTo(1)))
    } @@ failure,
    test("hasAt must succeed when a value is equal to a specific assertion") {
      assert(Seq(1, 2, 3), hasAt(1)(equalTo(2)))
    },
    test("isCase must fail when unapply fails (returns None)") {
      assert(42, isCase[Int, String](termName = "term", _ => None, equalTo("number: 42")))
    } @@ failure,
    test("isCase must succeed when unapplied Proj satisfy specified assertion")(
      assert(
        sampleUser,
        isCase[SampleUser, (String, Int)](
          termName = "SampleUser",
          { case SampleUser(name, age) => Some((name, age)) },
          equalTo((sampleUser.name, sampleUser.age))
        )
      )
    ),
    test("isFalse must succeed when supplied value is false") {
      assert(false, isFalse)
    },
    test("isGreaterThan must succeed when specified value is greater than supplied value") {
      assert(42, isGreaterThan(0))
    },
    test("isGreaterThan must fail when specified value is less than or equal supplied value") {
      assert(42, isGreaterThan(42))
    } @@ failure,
    test("greaterThanEqualTo must succeed when specified value is greater than or equal supplied value") {
      assert(42, isGreaterThanEqualTo(42))
    },
    test("isLeft must succeed when supplied value is Left and satisfy specified assertion") {
      assert(Left(42), isLeft(equalTo(42)))
    },
    test("isLessThan must succeed when specified value is less than supplied value") {
      assert(0, isLessThan(42))
    },
    test("isLessThan must fail when specified value is greater than or equal supplied value") {
      assert(42, isLessThan(42))
    } @@ failure,
    test("isLessThanEqualTo must succeed when specified value is less than or equal supplied value") {
      assert(42, isLessThanEqualTo(42))
    },
    test("isNone must succeed when specified value is None") {
      assert(None, isNone)
    },
    test("isNone must fail when specified value is not None") {
      assert(Some(42), isNone)
    } @@ failure,
    test("isRight must succeed when supplied value is Right and satisfy specified assertion") {
      assert(Right(42), isRight(equalTo(42)))
    },
    test("isSome must succeed when supplied value is Some and satisfy specified assertion") {
      assert(Some("zio"), isSome(equalTo("zio")))
    },
    test("isSome must fail when supplied value is None") {
      assert(None, isSome(equalTo("zio")))
    } @@ failure,
    test("isTrue must succeed when supplied value is true") {
      assert(true, isTrue)
    },
    test("isUnit must succeed when supplied value is ()") {
      assert((), isUnit)
    },
    test("isUnit must fail when supplied value is not ()") {
      assert(10, isUnit)
    } @@ failure,
    test("isWithin must succeed when supplied value is within range (inclusive)") {
      assert(10, isWithin(0, 10))
    },
    test("isWithin must fail when supplied value is out of range") {
      assert(42, isWithin(0, 10))
    } @@ failure,
    test("not must succeed when negation of specified assertion is true") {
      assert(0, not(equalTo(42)))
    },
    test("nothing must always fail") {
      assert(42, nothing)
    } @@ failure,
    test("succeeds must succeed when supplied value is Exit.succeed and satisfy specified assertion") {
      assert(Exit.succeed("Some Error"), succeeds(equalTo("Some Error")))
    },
    test("succeeds must fail when supplied value is Exit.fail") {
      assert(Exit.fail("Some Error"), succeeds(equalTo("Some Error")))
    } @@ failure,
    test("throws must succeed when given assertion is correct") {
      assert(throw sampleException, throws(equalTo(sampleException)))
    },
    test("and must succeed when both assertions are satisfied") {
      assert(sampleUser, nameStartsWithU && ageGreaterThan20)
    },
    test("and must fail when one of assertions is not satisfied") {
      assert(sampleUser, nameStartsWithA && ageGreaterThan20)
    } @@ failure,
    test("or must succeed when one of assertions is satisfied") {
      assert(sampleUser, (nameStartsWithA || nameStartsWithU) && ageGreaterThan20)
    },
    test("or must fail when both assertions are not satisfied") {
      assert(sampleUser, nameStartsWithA || ageLessThan20)
    } @@ failure,
    test("negate must succeed when negation of assertion is true") {
      assert(sampleUser, nameStartsWithA.negate)
    },
    test("test must return true when given element satisfy assertion") {
      assert(nameStartsWithU.test(sampleUser), isTrue)
    },
    test("test must return false when given element does not satisfy assertion") {
      assert(nameStartsWithA.test(sampleUser), isFalse)
    },
    test("containsString must succeed when string is found") {
      assert("this is a value", containsString("is a"))
    },
    test("containsString must return false when the string is not contained") {
      assert("this is a value", containsString("_NOTHING_"))
    } @@ failure,
    test("isEmptyString must succeed when the string is empty") {
      assert("", isEmptyString)
    },
    test("isEmptyString must fail when the string is not empty") {
      assert("some string", isEmptyString)
    } @@ failure,
    test("isNonEmptyString must succeed when the string is not empty") {
      assert("some string", isNonEmptyString)
    },
    test("isNonEmptyString must fail when the string is empty") {
      assert("", isNonEmptyString)
    } @@ failure,
    test("equalsIgnoreCase must succeed when the supplied value matches") {
      assert("Some String", equalsIgnoreCase("some string"))
    },
    test("equalsIgnoreCase must fail when the supplied value does not match") {
      assert("Some Other String", equalsIgnoreCase("some string"))
    } @@ failure,
    test("startsWith must succeed when the supplied value starts with the specified string") {
      assert("zio", startsWith("z"))
    },
    test("startsWith must fail when the supplied value does not start with the specified string") {
      assert("zio", startsWith("o"))
    } @@ failure,
    test("endsWith must succeed when the supplied value ends with the specified string") {
      assert("zio", endsWith("o"))
    },
    test("endsWith must fail when the supplied value does not end with the specified string") {
      assert("zio", endsWith("z"))
    } @@ failure,
    test("matches must succeed when the string matches the regex") {
      assert("(123) 456-7890", matchesRegex("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$"))
    },
    test("matches must fail when the string does not match the regex") {
      assert("456-7890", matchesRegex("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$"))
    } @@ failure,
    test("approximatelyEquals must succeed when number is within range") {
      assert(5.5, approximatelyEquals(5.0, 3.0))
    },
    test("approximatelyEquals must fail when number is not within range") {
      assert(50.0, approximatelyEquals(5.0, 3.0))
    } @@ failure,
    test("isEmpty must succeed when the traversable is empty") {
      assert(Seq(), isEmpty)
    },
    test("isEmpty must fail when the traversable is not empty") {
      assert(Seq(1, 2, 3), isEmpty)
    } @@ failure,
    test("isNonEmpty must succeed when the traversable is not empty") {
      assert(Seq(1, 2, 3), isNonEmpty)
    },
    test("isNonEmpty must fail when the traversable is empty") {
      assert(Seq(), isNonEmpty)
    } @@ failure,
    test("containsTheSameElements must succeed when both iterables contain the same elements") {
      assert(Seq(1, 2, 3), hasSameElements(Seq(1, 2, 3)))
    },
    test("containsTheSameElements must fail when the iterables do not contain the same elements") {
      assert(Seq(1, 2, 3, 4), hasSameElements(Seq(1, 2, 3)))
    } @@ failure,
    test("containsTheSameElements must succeed when both iterables contain the same elements in different order") {
      assert(Seq(4, 3, 1, 2), hasSameElements(Seq(1, 2, 3, 4)))
    },
    test(
      "hasSameElements must fail when both iterables have the same size, have the same values but they appear a different number of times."
    ) {
      assert(
        Seq("a", "a", "b", "b", "b", "c", "c", "c", "c", "c"),
        hasSameElements(Seq("a", "a", "a", "a", "a", "b", "b", "c", "c", "c"))
      )
    } @@ failure,
    test("assertCompiles must succeed when string is valid code") {
      assertCompiles("1 + 1")
    },
    test("assertCompiles must fail when string is not valid Scala code") {
      assertCompiles("1 ++ 1")
    } @@ failure,
    test("assertCompiles must report error messages on Scala 2") {
      assert(
        assertCompiles("1 ++ 1").failures match {
          case Some(Value(failure)) => Some(failure.assertion.head.value)
          case _                    => None
        },
        isSome(equalTo(Some("value ++ is not a member of Int")))
      )
    } @@ scala2Only
  )

  case class SampleUser(name: String, age: Int)
  val sampleUser      = SampleUser("User", 42)
  val sampleException = new Exception

  val nameStartsWithA  = hasField[SampleUser, Boolean]("name", _.name.startsWith("A"), isTrue)
  val nameStartsWithU  = hasField[SampleUser, Boolean]("name", _.name.startsWith("U"), isTrue)
  val ageLessThan20    = hasField[SampleUser, Int]("age", _.age, isLessThan(20))
  val ageGreaterThan20 = hasField[SampleUser, Int]("age", _.age, isGreaterThan(20))

  val someException = new RuntimeException("Boom!")
}

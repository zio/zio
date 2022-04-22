package zio.test

//import zio.test.TestAspect.scala2Only
import zio.test.TestAspect.scala2Only
import zio.{Chunk, Exit}

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success}

object AssertionsSpec extends ZIOBaseSpec {
//  import zio.test.Assertion._
  import zio.test.internal.SmartAssertion._

  val failing = TestAspect.identity

  def spec: Spec[Annotations, TestFailure[Any]] =
    suite("AssertionSpec")(
      test("and must succeed when both assertions are satisfied") {
        smartAssert(sampleUser)(nameStartsWithU && ageGreaterThan20)
      },
      test("and must fail when one of assertions is not satisfied") {
        smartAssert(sampleUser)(nameStartsWithA && ageGreaterThan20)
      } @@ failing,
      test("anything must always succeeds") {
        smartAssert(42)(anything)
      },
      test("approximatelyEquals must succeed when number is within range") {
        smartAssert(5.5)(approximatelyEquals(5.0, 3.0))
      },
      test("approximatelyEquals must fail when number is not within range") {
        smartAssert(50.0)(approximatelyEquals(5.0, 3.0))
      } @@ failing,
      test("contains must succeed when iterable contains specified element") {
        smartAssert(Seq("zio", "scala"))(contains("zio"))
      },
      test("contains must fail when iterable does not contain specified element") {
        smartAssert(Seq("zio", "scala"))(contains("java"))
      } @@ failing,
      test("containsString must succeed when string is found") {
        smartAssert("this is a value")(containsString("is a"))
      },
      test("containsString must return false when the string is not contained") {
        smartAssert("this is a value")(containsString("_NOTHING_"))
      } @@ failing,
      test("dies must succeed when exception satisfy specified assertion") {
        smartAssert(Exit.die(someException))(dies(equalTo(someException)))
      },
      test("dies must fail when exception does not satisfy specified assertion") {
        smartAssert(Exit.die(new RuntimeException("Bam!")))(dies(equalTo(someException)))
      } @@ failing,
      test("diesWithA must succeed when given type assertion is correct") {
        smartAssert(Exit.die(customException))(diesWithA[CustomException])
      },
      test("endsWith must succeed when the supplied value ends with the specified sequence") {
        smartAssert(List(1, 2, 3, 4, 5))(endsWith(List(3, 4, 5)))
      },
      test("startsWith must fail when the supplied value does not end with the specified sequence") {
        smartAssert(List(1, 2, 3, 4, 5))(startsWith(List(2, 3, 4)))
      } @@ failing,
      test("endsWithString must succeed when the supplied value ends with the specified string") {
        smartAssert("zio")(endsWithString("o"))
      },
      test("endsWithString must fail when the supplied value does not end with the specified string") {
        smartAssert("zio")(startsWithString("a"))
      } @@ failing,
      test("equalsIgnoreCase must succeed when the supplied value matches") {
        smartAssert("Some String")(equalsIgnoreCase("some string"))
      },
      test("equalsIgnoreCase must fail when the supplied value does not match") {
        smartAssert("Some Other String")(equalsIgnoreCase("some string"))
      } @@ failing,
      test("equalTo must succeed when value equals specified value") {
        smartAssert(42)(equalTo(42))
      },
      test("equalTo must fail when value does not equal specified value") {
        smartAssert(0)(equalTo(42))
      } @@ failing,
      test("equalTo must succeed when array equals specified array") {
        smartAssert(Array(1, 2, 3))(equalTo(Array(1, 2, 3)))
      },
      test("equalTo must fail when array does not equal specified array") {
        smartAssert(Array(1, 2, 3))(equalTo(Array(1, 2, 4)))
      } @@ failing,
      test("equalTo must not have type inference issues") {
        smartAssert(List(1, 2, 3).filter(_ => false))(equalTo(List.empty))
      },
      test("equalTo must not compile when comparing two unrelated types") {
        val result = typeCheck("smartAssert(1)(equalTo(\"abc\"))")
        smartAssertM(result)(
          isLeft(
            (containsString("found   : zio.test.Assertion[String]") &&
              containsString("required: zio.test.Assertion[Int]")) ||
              containsString(
                "String and Int are unrelated types"
              )
          )
        )
      } @@ scala2Only,
      test("exists must succeed when at least one element of iterable satisfy specified assertion") {
        smartAssert(Seq(1, 42, 5))(exists(equalTo(42)))
      },
      test("exists must fail when all elements of iterable do not satisfy specified assertion") {
        smartAssert(Seq(1, 42, 5))(exists(equalTo(0)))
      } @@ failing,
      test("exists must fail when iterable is empty") {
        smartAssert(Seq[String]())(exists(hasField("length", _.length, isWithin(0, 3))))
      } @@ failing,
      test("fails must succeed when error value satisfy specified assertion") {
        smartAssert(Exit.fail("Some Error"))(fails(equalTo("Some Error")))
      },
      test("fails must fail when error value does not satisfy specified assertion") {
        smartAssert(Exit.fail("Other Error"))(fails(equalTo("Some Error")))
      } @@ failing,
      test("failsWithA must succeed when given type assertion is correct") {
        smartAssert(Exit.fail(customException))(failsWithA[CustomException])
      },
      test("forall must succeed when all elements of iterable satisfy specified assertion") {
        smartAssert(Seq("a", "bb", "ccc"))(forall(hasField("length", _.length, isWithin(0, 3))))
      },
      test("forall must fail when one element of iterable do not satisfy specified assertion") {
        smartAssert(Seq("a", "bb", "dddd"))(forall(hasField("length", _.length, isWithin(0, 3))))
      } @@ failing,
      test("forall must succeed when an iterable is empty") {
        smartAssert(Seq[String]())(forall(hasField("length", _.length, isWithin(0, 3))))
      },
      test("forall must work with iterables that are not lists") {
        smartAssert(SortedSet(1, 2, 3))(forall(isGreaterThan(0)))
      },
      test("hasSameElementsDistinct must succeed when iterable contains the specified elements") {
        smartAssert(Seq(1, 2, 3))(hasSameElementsDistinct(Set(1, 2, 3)))
      },
      test(
        "hasSameElementsDistinct must succeed when iterable contains duplicates of the specified elements"
      ) {
        smartAssert(Seq("a", "a", "b", "b", "b", "c", "c", "c", "c", "c"))(
          hasSameElementsDistinct(Set("a", "b", "c"))
        )
      },
      test("hasSameElementsDistinct must succeed when specified elements contain duplicates") {
        smartAssert(Seq(1, 2, 3))(hasSameElementsDistinct(Seq(1, 2, 3, 3)))
      },
      test("hasSameElementsDistinct must fail when iterable does not have all specified elements") {
        smartAssert(Seq(1, 2, 3, 4))(hasSameElementsDistinct(Set(1, 2, 3, 4, 5)))
      } @@ failing,
      test("hasSameElementsDistinct must fail when iterable contains unspecified elements") {
        smartAssert(Seq(1, 2, 3, 4))(hasSameElementsDistinct(Set(1, 2, 3)))
      } @@ failing,
      test("hasAt must fail when an index is outside of a sequence range") {
        smartAssert(Seq(1, 2, 3))(hasAt(-1)(anything))
      } @@ failing,
      test("hasAt must fail when an index is outside of a sequence range 2") {
        smartAssert(Seq(1, 2, 3))(hasAt(3)(anything))
      } @@ failing,
      test("hasAt must fail when a value is not equal to a specific assertion") {
        smartAssert(Seq(1, 2, 3))(hasAt(1)(equalTo(1)))
      } @@ failing,
      test("hasAt must succeed when a value is equal to a specific assertion") {
        smartAssert(Seq(1, 2, 3))(hasAt(1)(equalTo(2)))
      },
      test("hasAtLeastOneOf must succeed when iterable contains one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasAtLeastOneOf(Set("zio", "test", "java")))
      },
      test("hasAtLeastOneOf must succeed when iterable contains more than one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasAtLeastOneOf(Set("zio", "test", "scala")))
      },
      test("hasAtLeastOneOf must fail when iterable does not contain a specified element") {
        smartAssert(Seq("zio", "scala"))(hasAtLeastOneOf(Set("python", "rust")))
      } @@ failing,
      test("hasAtMostOneOf must succeed when iterable contains one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasAtMostOneOf(Set("zio", "test", "java")))
      },
      test("hasAtMostOneOf must succeed when iterable does not contain a specified element") {
        smartAssert(Seq("zio", "scala"))(hasAtMostOneOf(Set("python", "rust")))
      },
      test("hasAtMostOneOf must fail when iterable contains more than one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasAtMostOneOf(Set("zio", "test", "scala")))
      } @@ failing,
      test("hasField must succeed when field value satisfy specified assertion") {
        smartAssert(SampleUser("User", 23))(hasField[SampleUser, Int]("age", _.age, isWithin(0, 99)))
      },
      test("hasFirst must fail when an iterable is empty") {
        smartAssert(Seq())(hasFirst(anything))
      } @@ failing,
      test("hasFirst must succeed when a head is equal to a specific assertion") {
        smartAssert(Seq(1, 2, 3))(hasFirst(equalTo(1)))
      },
      test("hasFirst must fail when a head is not equal to a specific assertion") {
        smartAssert(Seq(1, 2, 3))(hasFirst(equalTo(100)))
      } @@ failing,
      test("hasIntersection must succeed when intersection satisfies specified assertion") {
        smartAssert(Seq(1, 2, 3))(hasIntersection(Seq(3, 4, 5))(hasSize(equalTo(1))))
      },
      test("hasIntersection must succeed when empty intersection satisfies specified assertion") {
        smartAssert(Seq(1, 2, 3))(hasIntersection(Seq(4, 5, 6))(isEmpty))
      },
      test("hasIntersection must fail when intersection does not satisfy specified assertion") {
        smartAssert(Seq(1, 2, 3))(hasIntersection(Seq(3, 4, 5))(isEmpty))
      } @@ failing,
      test("hasKey must succeed when map has key with value satisfying specified assertion") {
        smartAssert(Map("scala" -> 1))(hasKey("scala", equalTo(1)))
      },
      test("hasKey must fail when map does not have the specified key") {
        smartAssert(Map("scala" -> 1))(hasKey("java", equalTo(1)))
      } @@ failing,
      test("hasKey must fail when map has key with value not satisfying specified assertion") {
        smartAssert(Map("scala" -> 1))(hasKey("scala", equalTo(-10)))
      } @@ failing,
      test("hasKey must succeed when map has the specified key") {
        smartAssert(Map("scala" -> 1))(hasKey("scala"))
      },
      test("hasKeys must succeed when map has keys satisfying the specified assertion") {
        smartAssert(Map("scala" -> 1, "java" -> 1))(hasKeys(hasAtLeastOneOf(Set("scala", "java"))))
      },
      test("hasKeys must fail when map has keys not satisfying the specified assertion") {
        //    - hasKeys must fail when map has keys not satisfying the specified assertion
        //      Set(scala, java) did not satisfy contains(bash)
        //      `Map(Predef.ArrowAssoc("scala").->(1), Predef.ArrowAssoc("java").->(1))` = Map(scala -> 1, java -> 1) did not satisfy hasKeys(contains(bash))
        //      at AssertionsSpec.scala:214
        smartAssert(Map("scala" -> 1, "java" -> 1))(hasKeys(contains("bash")))
      } @@ failing,
      test("hasLast must fail when an iterable is empty") {
        smartAssert(Seq())(hasLast(anything))
      } @@ failing,
      test("hasLast must succeed when a last is equal to a specific assertion") {
        smartAssert(Seq(1, 2, 3))(hasLast(equalTo(3)))
      },
      test("hasLast must fail when a last is not equal to specific assertion") {
        smartAssert(Seq(1, 2, 3))(hasLast(equalTo(100)))
      } @@ failing,
      test("hasNoneOf must succeed when iterable does not contain one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasNoneOf(Set("python", "rust")))
      },
      test("hasNoneOf must succeed when iterable is empty") {
        smartAssert(Seq.empty[String])(hasNoneOf(Set("zio", "test", "java")))
      },
      test("hasNoneOf must fail when iterable contains a specified element") {
        smartAssert(Seq("zio", "scala"))(hasNoneOf(Set("zio", "test", "scala")))
      } @@ failing,
      test("hasOneOf must succeed when iterable contains exactly one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasOneOf(Set("zio", "test", "java")))
      },
      test("hasOneOf must fail when iterable contains more than one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasOneOf(Set("zio", "test", "scala")))
      } @@ failing,
      test("hasOneOf must fail when iterable does not contain at least one of the specified elements") {
        smartAssert(Seq("zio", "scala"))(hasOneOf(Set("python", "rust")))
      } @@ failing,
      test("hasSameElements must succeed when both iterables contain the same elements") {
        smartAssert(Seq(1, 2, 3))(hasSameElements(Seq(1, 2, 3)))
      },
      test("hasSameElements must fail when the iterables do not contain the same elements") {
        smartAssert(Seq(1, 2, 3, 4))(hasSameElements(Seq(1, 2, 3)) && hasSameElements(Seq(1, 2, 3, 4, 5)))
      } @@ failing,
      test("hasSameElements must succeed when both iterables contain the same elements in different order") {
        smartAssert(Seq(4, 3, 1, 2))(hasSameElements(Seq(1, 2, 3, 4)))
      },
      test(
        "hasSameElements must fail when both iterables have the same size, have the same values but they appear a different number of times."
      ) {
        smartAssert(Seq("a", "a", "b", "b", "b", "c", "c", "c", "c", "c"))(
          hasSameElements(Seq("a", "a", "a", "a", "a", "b", "b", "c", "c", "c"))
        )
      } @@ failing,
      test("hasSize must succeed when iterable size is equal to specified assertion") {
        smartAssert(Seq(1, 2, 3))(hasSize(equalTo(3)))
      },
      test("hasSize must fail when iterable size is not equal to specified assertion") {
        smartAssert(Seq(1, 2, 3))(hasSize(equalTo(1)))
      } @@ failing,
      test("hasSize must succeed when chunk size is equal to specified assertion") {
        smartAssert(Chunk(1, 2, 3))(hasSize(equalTo(3)))
      },
      test("hasSize must fail when chunk size is not equal to specified assertion") {
        smartAssert(Chunk(1, 2, 3))(hasSize(equalTo(1)))
      } @@ failing,
      test("hasSizeString must succeed when string size is equal to specified assertion") {
        smartAssert("aaa")(hasSizeString(equalTo(3)))
      },
      test("hasSizeString must fail when string size is not equal to specified assertion") {
        smartAssert("aaa")(hasSizeString(equalTo(2)))
      } @@ failing,
      test("hasSubset must succeed when both iterables contain the same elements") {
        smartAssert(Seq(1, 2, 3))(hasSubset(Set(1, 2, 3)))
      },
      test("hasSubset must succeed when the other iterable is a subset of the iterable") {
        smartAssert(Seq(1, 2, 3, 4))(hasSubset(Set(1, 2, 3)))
      },
      test("hasSubset must succeed when the other iterable is empty") {
        smartAssert(Seq(4, 3, 1, 2))(hasSubset(Set.empty[Int]))
      },
      test("hasSubset must fail when iterable does not contain elements specified in set") {
        smartAssert(Seq(4, 3, 1, 2))(hasSubset(Set(1, 2, 10)))
      } @@ failing,
      test("hasValues must succeed when map has values satisfying the specified assertion") {
        smartAssert(Map("scala" -> 10, "java" -> 20))(hasValues(hasAtLeastOneOf(Set(0, 10))))
      },
      test("hasValues must fail when map has values not satisfying the specified assertion") {
        smartAssert(Map("scala" -> 10, "java" -> 20))(hasValues(contains(0)))
      } @@ failing,
      test("isCase must fail when unapply fails (returns None)") {
        smartAssert(42)(isCase[Int, String](termName = "term", _ => None, equalTo("number: 42")))
      } @@ failing,
      test("isCase must succeed when unapplied Proj satisfy specified assertion")(
        smartAssert(sampleUser)(
          isCase[SampleUser, (String, Int)](
            termName = "SampleUser",
            { case SampleUser(name, age) => Some((name, age)) },
            equalTo((sampleUser.name, sampleUser.age))
          )
        )
      ),
      test("isDistinct must succeed when iterable is distinct") {
        smartAssert(Seq(1, 2, 3, 4, 0))(isDistinct)
      },
      test("isDistinct must succeed for empty iterable") {
        smartAssert(Seq.empty[Int])(isDistinct)
      },
      test("isDistinct must succeed for singleton iterable") {
        smartAssert(Seq(1))(isDistinct)
      },
      test("isDistinct must fail when iterable is not distinct") {
        smartAssert(Seq(1, 2, 3, 3, 4))(isDistinct)
      } @@ failing,
      test("isEmpty must succeed when the traversable is empty") {
        smartAssert(Seq())(isEmpty)
      },
      test("isEmpty must fail when the traversable is not empty") {
        smartAssert(Seq(1, 2, 3))(isEmpty)
      } @@ failing,
      test("isEmptyString must succeed when the string is empty") {
        smartAssert("")(isEmptyString)
      },
      test("isEmptyString must fail when the string is not empty") {
        smartAssert("some string")(isEmptyString)
      } @@ failing,
      test("isFalse must succeed when supplied value is false") {
        smartAssert(false)(isFalse)
      },
      test("isFailure must succeed when Failure value satisfies the specified assertion") {
        smartAssert(Failure(new Exception("oh no!")))(isFailure(hasMessage(equalTo("oh no!"))))
      },
      test("isFailure must succeed when Try value is Failure") {
        smartAssert(Failure(new Exception("oh no!")))(isFailure)
      },
      test("isGreaterThan must succeed when specified value is greater than supplied value") {
        smartAssert(42)(isGreaterThan(0))
      },
      test("isGreaterThan must fail when specified value is less than or equal supplied value") {
        smartAssert(42)(isGreaterThan(42))
      } @@ failing,
      test("isGreaterThanEqualTo must succeed when specified value is greater than or equal supplied value") {
        smartAssert(42)(isGreaterThanEqualTo(42))
      },
      test("isLeft must succeed when supplied value is Left and satisfy specified assertion") {
        smartAssert(Left(42))(isLeft(equalTo(42)))
      },
      test("isLeft must succeed when supplied value is Left") {
        smartAssert(Left(42))(isLeft)
      },
      test("isLeft must fail when supplied value is Right") {
        smartAssert(Right(-42))(isLeft)
      } @@ failing,
      test("isLessThan must succeed when specified value is less than supplied value") {
        smartAssert(0)(isLessThan(42))
      },
      test("isLessThan must fail when specified value is greater than or equal supplied value") {
        smartAssert(42)(isLessThan(42))
      } @@ failing,
      test("isLessThanEqualTo must succeed when specified value is less than or equal supplied value") {
        smartAssert(42)(isLessThanEqualTo(42))
      },
      test("isNegative must succeed when number is negative") {
        smartAssert(-10)(isNegative)
      },
      test("isNegative must fail when number is zero") {
        smartAssert(0)(isNegative)
      } @@ failing,
      test("isNegative must fail when number is positive") {
        smartAssert(10)(isNegative)
      } @@ failing,
      test("isNone must succeed when specified value is None") {
        smartAssert(None)(isNone)
      },
      test("isNone must fail when specified value is not None") {
        smartAssert(Some(42))(isNone)
      } @@ failing,
      test("isNonEmpty must succeed when the traversable is not empty") {
        smartAssert(Seq(1, 2, 3))(isNonEmpty)
      },
      test("isNonEmpty must fail when the chunk is empty") {
        smartAssert(Chunk.empty)(isNonEmpty)
      } @@ failing,
      test("isNonEmpty must succeed when the chunk is not empty") {
        smartAssert(Chunk(1, 2, 3))(isNonEmpty)
      },
      test("isNonEmpty must fail when the traversable is empty") {
        smartAssert(Seq())(isNonEmpty)
      } @@ failing,
      test("isNonEmptyString must succeed when the string is not empty") {
        smartAssert("some string")(isNonEmptyString)
      },
      test("isNonEmptyString must fail when the string is empty") {
        smartAssert("")(isNonEmptyString)
      } @@ failing,
      test("isNull must succeed when specified value is null") {
        smartAssert(null)(isNull)
      },
      test("isNull must fail when specified value is not null") {
        smartAssert("not null")(isNull)
      } @@ failing,
      test("isOneOf must succeed when value is equal to one of the specified values") {
        smartAssert('a')(isOneOf(Set('a', 'b', 'c')))
      },
      test("isOneOf must fail when value is not equal to one of the specified values") {
        smartAssert('z')(isOneOf(Set('a', 'b', 'c')))
      } @@ failing,
      test("isPositive must succeed when number is positive") {
        smartAssert(10)(isPositive)
      },
      test("isPositive must fail when number is zero") {
        smartAssert(0)(isPositive)
      } @@ failing,
      test("isPositive must fail when number is negative") {
        smartAssert(-10)(isPositive)
      } @@ failing,
      test("isRight must succeed when supplied value is Right and satisfy specified assertion") {
        smartAssert(Right(42))(isRight(equalTo(42)))
      },
      test("isRight must succeed when supplied value is Right") {
        smartAssert(Right(42))(isRight)
      },
      test("isSome must succeed when supplied value is Some and satisfy specified assertion") {
        smartAssert(Some("zio"))(isSome(equalTo("zio")))
      },
      test("isSome with isOneOf must succeed when assertion is satisfied") {
        smartAssert(Some("zio"))(isSome(isOneOf(Set("zio", "test"))))
      },
      test("isSome must fail when supplied value is None") {
        smartAssert(None)(isSome(equalTo("zio")))
      } @@ failing,
      test("isSome must succeed when supplied value is Some") {
        smartAssert(Some("zio"))(isSome)
      },
      test("isSome must fail when supplied value is None") {
        smartAssert(None)(isSome)
      } @@ failing,
      test("isSorted must succeed when iterable is sorted") {
        smartAssert(Seq(1, 2, 2, 3, 4))(isSorted)
      },
      test("isSorted must succeed for empty iterable") {
        smartAssert(Seq.empty[Int])(isSorted)
      },
      test("isSorted must succeed for singleton iterable") {
        smartAssert(Seq(1))(isSorted)
      },
      test("isSorted must fail when iterable is not sorted") {
        smartAssert(Seq(1, 2, 0, 3, 4))(isSorted)
      } @@ failing,
      test("isSortedReverse must succeed when iterable is reverse sorted") {
        smartAssert(Seq(4, 3, 3, 2, 1))(isSortedReverse)
      },
      test("isSortedReverse must fail when iterable is not reverse sorted") {
        smartAssert(Seq(1, 2, 0, 3, 4))(isSortedReverse)
      } @@ failing,
      test("isSubtype must succeed when value is subtype of specified type") {
        smartAssert(dog)(isSubtype[Animal](anything))
      },
      test("isSubtype must fail when value is supertype of specified type") {
        smartAssert(animal)(isSubtype[Cat](anything))
      } @@ failing,
      test("isSubtype must fail when value is neither subtype nor supertype of specified type") {
        smartAssert(cat)(isSubtype[Dog](anything))
      } @@ failing,
      test("isSubtype must handle primitive types") {
        smartAssert(1)(isSubtype[Int](anything))
      },
      test("isSubtype must handle malformed class names") {
        sealed trait Exception
        object Exception {
          case class MyException() extends Exception
        }
        val exception = new Exception.MyException
        smartAssert(exception)(isSubtype[Exception.MyException](anything))
      },
      test("isSuccess must succeed when Success value satisfies the specified assertion") {
        smartAssert(Success(1))(isSuccess(isPositive[Int]))
      },
      test("isSuccess must succeed when Try value is Success") {
        smartAssert(Success(1))(isSuccess)
      },
      test("isTrue must succeed when supplied value is true") {
        smartAssert(true)(isTrue)
      },
      test("isUnit must succeed when supplied value is ()") {
        smartAssert(())(isUnit)
      },
      test("isUnit must not compile when supplied value is not ()") {
        val result = typeCheck("smartAssert(10)(isUnit)")
        smartAssertM(result)(isLeft(anything))
      },
      test("isWithin must succeed when supplied value is within range (inclusive)") {
        smartAssert(10)(isWithin(0, 10))
      },
      test("isWithin must fail when supplied value is out of range") {
        smartAssert(42)(isWithin(0, 10))
      } @@ failing,
      test("isZero must succeed when number is zero") {
        smartAssert(0)(isZero)
      },
      test("isZero must fail when number is not zero") {
        smartAssert(10)(isZero)
      } @@ failing,
      test("matches must succeed when the string matches the regex") {
        smartAssert("(123) 456-7890")(matchesRegex("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$"))
      },
      test("matches must fail when the string does not match the regex") {
        smartAssert("456-7890")(matchesRegex("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$"))
      } @@ failing,
      test("negate must succeed when negation of assertion is true") {
        smartAssert(sampleUser)(nameStartsWithA.negate)
      },
      test("nonNegative must succeed when number is positive") {
        smartAssert(10)(nonNegative)
      },
      test("nonNegative must succeed when number is zero") {
        smartAssert(0)(nonNegative)
      },
      test("nonNegative must fail when number is negative") {
        smartAssert(-10)(nonNegative)
      } @@ failing,
      test("nonPositive must succeed when number is negative") {
        smartAssert(-10)(nonPositive)
      },
      test("nonPositive must succeed when number is zero") {
        smartAssert(0)(nonPositive)
      },
      test("nonPositive must fail when number is positive") {
        smartAssert(10)(nonPositive)
      } @@ failing,
      test("not must succeed when negation of specified assertion is true") {
        smartAssert(0)(not(equalTo(42)))
      },
      test("nothing must always fail") {
        smartAssert(42)(nothing)
      } @@ failing,
      test("or must succeed when one of assertions is satisfied") {
        smartAssert(sampleUser)((nameStartsWithA || nameStartsWithU) && ageGreaterThan20)
      },
      test("or must fail when both assertions are not satisfied") {
        smartAssert(sampleUser)(nameStartsWithA || ageLessThan20)
      } @@ failing,
      test("startsWith must succeed when the supplied value starts with the specified sequence") {
        smartAssert(List(1, 2, 3, 4, 5))(startsWith(List(1, 2, 3)))
      },
      test("startsWith must fail when the supplied value does not start with the specified sequence") {
        smartAssert(List(1, 2, 3, 4, 5))(startsWith(List(3, 4, 5)))
      } @@ failing,
      test("startsWithString must succeed when the supplied value starts with the specified string") {
        smartAssert("zio")(startsWithString("z"))
      },
      test("startsWithString must fail when the supplied value does not start with the specified string") {
        smartAssert("zio")(startsWithString("o"))
      } @@ failing,
      test("succeeds must succeed when supplied value is Exit.succeed and satisfy specified assertion") {
        smartAssert(Exit.succeed("Some Error"))(succeeds(equalTo("Some Error")))
      },
      test("succeeds must fail when supplied value is Exit.fail") {
        smartAssert(Exit.fail("Some Error"))(succeeds(equalTo("Some Error")))
      } @@ failing,
      test("test must return true when given element satisfy assertion") {
        smartAssert(nameStartsWithU.test(sampleUser))(isTrue)
      },
      test("test must return false when given element does not satisfy assertion") {
        smartAssert(nameStartsWithA.test(sampleUser))(isFalse)
      },
      test("throws must succeed when given assertion is correct") {
        smartAssert(throw sampleException)(throws(equalTo(sampleException)))
      },
      test("throwsA must succeed when given type assertion is correct") {
        smartAssert(throw customException)(throwsA[CustomException])
      },
      test("should implement equals without exception") {
        smartAssert(nameStartsWithU.equals(new Object))(isFalse)
      },
      test("hasThrowableCause must succeed when supplied value has matching cause") {
        val cause = new Exception("cause")
        val t     = new Exception("result", cause)
        smartAssert(t)(hasThrowableCause(hasMessage(equalTo("cause"))))
      },
      test("hasThrowableCause must fail when supplied value has non-matching cause") {
        val cause = new Exception("something different")
        val t     = new Exception("result", cause)
        smartAssert(t)(hasThrowableCause(hasMessage(equalTo("cause"))))
      } @@ failing,
      test("hasThrowableCause must fail when supplied value does not have a cause") {
        val t = new Exception("result")
        smartAssert(t)(hasThrowableCause(hasMessage(equalTo("cause"))))
      } @@ failing
    )

  case class SampleUser(name: String, age: Int)
  val sampleUser: SampleUser = SampleUser("User", 42)
  val sampleException        = new Exception

  val nameStartsWithA: Assertion[SampleUser]  = hasField("name", _.name.startsWith("A"), isTrue)
  val nameStartsWithU: Assertion[SampleUser]  = hasField("name", _.name.startsWith("U"), isTrue)
  val ageLessThan20: Assertion[SampleUser]    = hasField("age", _.age, isLessThan(20))
  val ageGreaterThan20: Assertion[SampleUser] = hasField("age", _.age, isGreaterThan(20))

  val someException = new RuntimeException("Boom!")

  case class CustomException() extends RuntimeException("Custom Boom!")
  val customException: CustomException = CustomException()

  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  val animal: Animal = new Animal {}
  val dog: Dog       = new Dog {}
  val cat: Cat       = new Cat {}

}

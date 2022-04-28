package fix

import zio.test.Assertion._
import zio.test._
import zio.test.TestAspect._
import zio.{Chunk, Exit}

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success}

object AssertionSpec extends DefaultRunnableSpec {

  def spec: Spec[Annotations, TestFailure[Any], TestSuccess] = suite("AssertionSpec")(
//    test("and must succeed when both assertions are satisfied") {
//      assert(sampleUser)(nameStartsWithU && ageGreaterThan20)
//    },
//    test("and must fail when one of assertions is not satisfied") {
//      assert(sampleUser)(nameStartsWithA && ageGreaterThan20)
//    } @@ failing,
    test("anything must always succeeds") {
      assertTrue((42).is(_.anything))
    },
//    test("approximatelyEquals must succeed when number is within range") {
//      assert(5.5)(approximatelyEquals(5.0, 3.0))
//    },
//    test("approximatelyEquals must fail when number is not within range") {
//      assert(50.0)(approximatelyEquals(5.0, 3.0))
//    } @@ failing,
    test("contains must succeed when iterable contains specified element") {
      assertTrue((Seq("zio", "scala")).contains("zio"))
    },
    test("contains must fail when iterable does not contain specified element") {
      assertTrue((Seq("zio", "scala")).contains("java"))
    } @@ failing,
    test("containsString must succeed when string is found") {
      assertTrue(("this is a value").contains("is a"))
    },
    test("containsString must return false when the string is not contained") {
      assertTrue(("this is a value").contains("_NOTHING_"))
    } @@ failing,
    test("dies must succeed when exception satisfy specified assertion") {
      assertTrue((Exit.die(someException)).is(_.die.anything))
    },
    test("dies must fail when exception does not satisfy specified assertion") {
      assertTrue((Exit.die(new RuntimeException("Bam!"))).is(_.die) == someException)
    } @@ failing,
    test("endWith must succeed when the supplied value ends with the specified sequence") {
      assertTrue((List(1, 2, 3, 4, 5)).endsWith(List(3, 4, 5)))
    },
    test("startsWith must fail when the supplied value does not end with the specified sequence") {
      assertTrue((List(1, 2, 3, 4, 5)).endsWith(List(1, 2, 3)))
    } @@ failing,
    test("endsWithString must succeed when the supplied value ends with the specified string") {
      assertTrue(("zio").endsWithString("o"))
    },
    test("endsWithString must fail when the supplied value does not end with the specified string") {
      assertTrue(("zio").endsWithString("z"))
    } @@ failing,
    test("equalsIgnoreCase must succeed when the supplied value matches") {
      assertTrue(("Some String").is(_.equalsIgnoreCase("some string")))
    },
    test("equalsIgnoreCase must fail when the supplied value does not match") {
      assertTrue(("Some Other String").is(_.equalsIgnoreCase("some string")))
    } @@ failing,
    test("equalTo must succeed when value equals specified value") {
      assertTrue((42) == 42)
    },
    test("equalTo must fail when value does not equal specified value") {
      assertTrue((0) == 42)
    } @@ failing,
    test("equalTo must succeed when array equals specified array") {
      assertTrue((Array(1, 2, 3)) == Array(1, 2, 3))
    },
    test("equalTo must fail when array does not equal specified array") {
      assertTrue((Array(1, 2, 3)) == Array(1, 2, 4))
    } @@ failing,
    test("equalTo must not have type inference issues") {
      assert(List(1, 2, 3).filter(_ => false))(equalTo(List.empty))
      assertTrue((List(1, 2, 3).filter(_ => false)) == List.empty)
    },
    test("equalTo must not compile when comparing two unrelated types") {
      val result = typeCheck("assert(1)(equalTo(\"abc\"))")
      assertM(result)(
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
      assertTrue((Seq(1, 42, 5)).exists(_ == (42)))
    },
    test("exists must fail when all elements of iterable do not satisfy specified assertion") {
      assertTrue((Seq(1, 42, 5)).exists(_ == (0)))
    } @@ failing,
    test("exists must fail when iterable is empty") {
      assertTrue((Seq[String]()).exists(_.length.is(_.within(0, 3))))
    } @@ failing,
    test("fails must succeed when error value satisfy specified assertion") {
      assertTrue((Exit.fail("Some Error")).is(_.fail) == "Some Error")
    },
    test("fails must fail when error value does not satisfy specified assertion") {
      assertTrue((Exit.fail("Other Error")).is(_.fail) == "Some Error")
    } @@ failing,
    test("forall must succeed when all elements of iterable satisfy specified assertion") {
      assertTrue((Seq("a", "bb", "ccc")).forall(_.length.is(_.within(0, 3))))
    },
    test("forall must fail when one element of iterable do not satisfy specified assertion") {
      assertTrue((Seq("a", "bb", "dddd")).forall(_.length.is(_.within(0, 3))))
    } @@ failing,
    test("forall must succeed when an iterable is empty") {
      assertTrue((Seq[String]()).forall(_.length.is(_.within(0, 3))))
    },
    test("forall must work with iterables that are not lists") {
      assertTrue((SortedSet(1, 2, 3)).forall(_.>(0)))
    },
    test("hasSameElementsDistinct must succeed when iterable contains the specified elements") {
      assert(Seq(1, 2, 3))(hasSameElementsDistinct(Set(1, 2, 3)))
    },
    test(
      "hasSameElementsDistinct must succeed when iterable contains duplicates of the specified elements"
    ) {
      assert(Seq("a", "a", "b", "b", "b", "c", "c", "c", "c", "c"))(
        hasSameElementsDistinct(Set("a", "b", "c"))
      )
    },
    test("hasSameElementsDistinct must succeed when specified elements contain duplicates") {
      assertTrue((Seq(1, 2, 3)).is(_.hasSameElementsDistinct(Seq(1, 2, 3, 3))))
    },
    test("hasSameElementsDistinct must fail when iterable does not have all specified elements") {
      assertTrue((Seq(1, 2, 3, 4)).is(_.hasSameElementsDistinct(Set(1, 2, 3, 4,5 ))))
    } @@ failing,
    test("hasSameElementsDistinct must fail when iterable contains unspecified elements") {
      assertTrue((Seq(1, 2, 3, 4)).is(_.hasSameElementsDistinct(Set(1, 2, 3))))
    } @@ failing,
    test("hasAt must fail when an index is outside of a sequence range") {
      assertTrue((Seq(1, 2, 3))(-1).is(_.anything))
    } @@ failing,
    test("hasAt must fail when an index is outside of a sequence range 2") {
      assertTrue((Seq(1, 2, 3))(3).is(_.anything))
    } @@ failing,
    test("hasAt must fail when a value is not equal to a specific assertion") {
      assertTrue((Seq(1, 2, 3))(1) == 1)
    } @@ failing,
    test("hasAt must succeed when a value is equal to a specific assertion") {
      assertTrue((Seq(1, 2, 3))(1) == 2)
    },
    test("hasAtLeastOneOf must succeed when iterable contains one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasAtLeastOneOf(Set("zio", "test", "java"))))
    },
    test("hasAtLeastOneOf must succeed when iterable contains more than one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasAtLeastOneOf(Set("zio", "test", "scala"))))
    },
    test("hasAtLeastOneOf must fail when iterable does not contain a specified element") {
      assertTrue((Seq("zio", "scala")).is(_.hasAtLeastOneOf(Set("python", "rust"))))
    } @@ failing,
    test("hasAtMostOneOf must succeed when iterable contains one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasAtMostOneOf(Set("zio", "test", "scala"))))
    },
    test("hasAtMostOneOf must succeed when iterable does not contain a specified element") {
      assertTrue((Seq("zio", "scala")).is(_.hasAtMostOneOf(Set("python", "rust"))))
    },
    test("hasAtMostOneOf must fail when iterable contains more than one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasAtMostOneOf(Set("zio", "test", "scala"))))
    } @@ failing,
    test("hasField must succeed when field value satisfy specified assertion") {
      assertTrue((SampleUser("User", 23)).age.is(_.isWithin(0, 99)))
    },
    test("hasFirst must fail when an iterable is empty") {
      assertTrue((Seq()).head.is(_.anything))
    } @@ failing,
    test("hasFirst must succeed when a head is equal to a specific assertion") {
      assertTrue((Seq(1, 2, 3)).head == 1)
    },
    test("hasFirst must fail when a head is not equal to a specific assertion") {
      assertTrue((Seq(1, 2, 3)).head == 100)
    } @@ failing,
    test("hasIntersection must succeed when intersection satisfies specified assertion") {
      assertTrue((Seq(1, 2, 3)).intersect(Seq(3, 4, 5)).size == 1)
    },
    test("hasIntersection must succeed when empty intersection satisfies specified assertion") {
      assertTrue((Seq(1, 2, 3)).intersect(Seq(4, 5, 6)).isEmpty)
    },
    test("hasIntersection must fail when intersection does not satisfy specified assertion") {
      assertTrue((Seq(1, 2, 3)).intersect(Seq(3, 4, 5)).isEmpty)
    } @@ failing,
    test("hasKey must succeed when map has key with value satisfying specified assertion") {
      assertTrue((Map("scala" -> 1))("scala") == 1)
    },
    },
    test("hasKey must fail when map does not have the specified key") {
      assertTrue((Map("scala" -> 1))("java") == 1)
    } @@ failing,
    test("hasKey must fail when map has key with value not satisfying specified assertion") {
        assertTrue((Map("scala" -> 1))("scala") == -10)
    } @@ failing,
    test("hasKey must succeed when map has the specified key") {
      assertTrue((Map("scala" -> 1))("scala") == 1)
    },
    test("hasKeys must succeed when map has keys satisfying the specified assertion") {
      assertTrue((Map("scala" -> 1, "java" -> 1)).keys.is(_.hasAtLeastOneOf(Set("scala", "java"))))
    },
    test("hasKeys must fail when map has keys not satisfying the specified assertion") {
        assertTrue((Map("scala" -> 1, "java" -> 1)).keys.contains("bash"))
    },
    } @@ failing,
    test("hasLast must fail when an iterable is empty") {
      assertTrue((Seq()).last.is(_.anything))
    },
    } @@ failing,
    test("hasLast must succeed when a last is equal to a specific assertion") {
      assertTrue((Seq(1, 2, 3)).last == 3)
    },
    test("hasLast must fail when a last is not equal to specific assertion") {
      assertTrue((Seq(1, 2, 3)).last == 100)
    } @@ failing,
    test("hasNoneOf must succeed when iterable does not contain one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasNoneOf(Set("python", "rust"))))
    },
    test("hasNoneOf must succeed when iterable is empty") {
      assertTrue((Seq.empty[String]).is(_.hasNoneOf(Set("zio", "test", "java"))))
    },
    test("hasNoneOf must fail when iterable contains a specified element") {
      assertTrue((Seq("zio", "scala")).is(_.hasNoneOf(Set("zio", "test", "scala"))))
    } @@ failing,
    test("hasOneOf must succeed when iterable contains exactly one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasOneOf(Set("zio", "test", "java"))))
    },
    test("hasOneOf must fail when iterable contains more than one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasOneOf(Set("zio", "test", "scala"))))
    },
    } @@ failing,
    test("hasOneOf must fail when iterable does not contain at least one of the specified elements") {
      assertTrue((Seq("zio", "scala")).is(_.hasOneOf(Set("python", "rust"))))
    } @@ failing,
    test("hasSameElements must succeed when both iterables contain the same elements") {
      assertTrue((Seq(1, 2, 3)).is(_.hasSameElements(Seq(1, 2, 3))))
    },
    test("hasSameElements must fail when the iterables do not contain the same elements") {
      assertTrue((Seq(1, 2, 3, 4)).is(_.hasSameElements(Seq(1, 2, 3)) && Seq(1, 2, 3, 4).hasSameElements(Seq(1, 2, 3, 4, 5))))
    } @@ failing,
    test("hasSameElements must succeed when both iterables contain the same elements in different order") {
      assertTrue((Seq(4, 3, 1, 2)).is(_.hasSameElements(Seq(1, 2, 3, 4))))
    },
    test(
      "hasSameElements must fail when both iterables have the same size, have the same values but they appear a different number of times."
    ) {
      assertTrue((Seq("a", "a", "b", "b", "b", "c", "c", "c", "c", "c")).is(_.hasSameElements(Seq("a", "a", "a", "a", "a", "b", "b", "c", "c", "c"))))
    },
    } @@ failing,
    test("hasSize must succeed when iterable size is equal to specified assertion") {
      assertTrue((Seq(1, 2, 3)).size == 3)
    },
    test("hasSize must fail when iterable size is not equal to specified assertion") {
     assertTrue((Seq(1, 2, 3)).size == 1)
    } @@ failing,
    test("hasSize must succeed when chunk size is equal to specified assertion") {
      assertTrue((Chunk(1, 2, 3)).size == 3)
    },
    test("hasSize must fail when chunk size is not equal to specified assertion") {
      assertTrue((Chunk(1, 2, 3)).size == 1)
    } @@ failing,
    test("hasSizeString must succeed when string size is equal to specified assertion") {
      assertTrue("aaa".length == 3)
    },
    test("hasSizeString must fail when string size is not equal to specified assertion") {
      assertTrue("aaa".length == 2)
    } @@ failing,
    test("hasSubset must succeed when both iterables contain the same elements") {
      assertTrue((Seq(1, 2, 3)).is(_.hasSubset(Set(1, 2, 3))))
    },
    test("hasSubset must succeed when the other iterable is a subset of the iterable") {
      assertTrue((Seq(1, 2, 3, 4)).is(_.hasSubset(Set(1, 2, 3))))
    },
    test("hasSubset must succeed when the other iterable is empty") {
      assertTrue((Seq(4, 3, 1, 2)).is(_.hasSubset(Set.empty[Int])))
    },
    test("hasSubset must fail when iterable does not contain elements specified in set") {
      assertTrue((Seq(4, 3, 1, 2)).is(_.hasSubset(Set(1, 2, 10))))
    } @@ failing,
    test("hasValues must succeed when map has values satisfying the specified assertion") {
      assertTrue((Map("scala" -> 10, "java" -> 20)).values.is(_.hasAtLeastOneOf(Set(0, 10))))
    },
    test("hasValues must fail when map has values not satisfying the specified assertion") {
      assertTrue((Map("scala" -> 10, "java" -> 20)).values.contains(0))
    } @@ failing,
    test("isDistinct must succeed when iterable is distinct") {
      assertTrue((Seq(1, 2, 3, 4, 0)).is(_.distinct))
    },
    test("isDistinct must succeed for empty iterable") {
      assertTrue((Seq.empty[Int]).is(_.distinct))
    },
    test("isDistinct must succeed for singleton iterable") {
      assertTrue((Seq(1)).is(_.distinct))
    },
    test("isDistinct must fail when iterable is not distinct") {
      assertTrue((Seq(1, 2, 3, 3, 4)).is(_.distinct))
    } @@ failing,
    test("isEmpty must succeed when the traversable is empty") {
      assertTrue((Seq()).is(_.isEmpty))
    },
    test("isEmpty must fail when the traversable is not empty") {
      assertTrue((Seq(1, 2, 3)).is(_.isEmpty))
    } @@ failing,
    test("isEmptyString must succeed when the string is empty") {
      assertTrue(("").isEmpty)
    },
    test("isEmptyString must fail when the string is not empty") {
      assertTrue(("some string").isEmpty)
    } @@ failing,
    test("isFalse must succeed when supplied value is false") {
      assertTrue((false) == false)
    },
    test("isFailure must succeed when Failure value satisfies the specified assertion") {
      assertTrue((Failure(new Exception("oh no!")).is(_.failure).message == "oh no!"))
    },
    test("isFailure must succeed when Try value is Failure") {
      assertTrue((Failure(new Exception("oh no!"))).is(_.failure.anything))
    },
    test("isGreaterThan must succeed when specified value is greater than supplied value") {
      assertTrue((42) > 0)
    },
    test("isGreaterThan must fail when specified value is less than or equal supplied value") {
      assertTrue((42) > 42)
    } @@ failing,
    test("isGreaterThanEqualTo must succeed when specified value is greater than or equal supplied value") {
     assertTrue((42) >= 42)
    },
    test("isLeft must succeed when supplied value is Left and satisfy specified assertion") {
      assertTrue((Left(42)).is(_.left) == 42)
    },
    test("isLeft must succeed when supplied value is Left") {
      assertTrue((Left(42)).is(_.left.anything))
    },
    test("isLeft must fail when supplied value is Right") {
      assertTrue((Right(-42)).is(_.left.anything))
    } @@ failing,
    test("isLessThan must succeed when specified value is less than supplied value") {
      assertTrue((0) < 42)
    },
    test("isLessThan must fail when specified value is greater than or equal supplied value") {
      assertTrue((42) < 42)
    } @@ failing,
    test("isLessThanEqualTo must succeed when specified value is less than or equal supplied value") {
      assertTrue((42) <= 42)
    },
    test("isNegative must succeed when number is negative") {
      assertTrue((-10) < 0)
    },
    test("isNegative must fail when number is zero") {
      assertTrue((0) < 0)
    } @@ failing,
    test("isNegative must fail when number is positive") {
      assertTrue((10) < 0)
    } @@ failing,
    test("isNone must succeed when specified value is None") {
      assertTrue((None).isEmpty)
    },
    test("isNone must fail when specified value is not None") {
      assertTrue((Some(42)).isEmpty)
    } @@ failing,
    test("isNonEmpty must succeed when the traversable is not empty") {
      assertTrue((Seq(1, 2, 3)).nonEmpty)
    },
    test("isNonEmpty must fail when the chunk is empty") {
      assertTrue((Chunk.empty).nonEmpty)
    } @@ failing,
    test("isNonEmpty must succeed when the chunk is not empty") {
      assertTrue((Chunk(1, 2, 3)).nonEmpty)
    },
    test("isNonEmpty must fail when the traversable is empty") {
      assertTrue((Seq()).nonEmpty)
    } @@ failing,
    test("isNonEmptyString must succeed when the string is not empty") {
      assertTrue(("some string").nonEmpty)
    },
    test("isNonEmptyString must fail when the string is empty") {
        assertTrue(("").nonEmpty)
    } @@ failing,
    test("isNull must succeed when specified value is null") {
      assertTrue((null) == null)
    },
    test("isNull must fail when specified value is not null") {
       assertTrue(("not null") == null)
    } @@ failing,
    test("isOneOf must succeed when value is equal to one of the specified values") {
      assertTrue(('a').is(_.oneOf(Set('a', 'b', 'c'))))
    },
    test("isOneOf must fail when value is not equal to one of the specified values") {
      assertTrue(('z').is(_.oneOf(Set('a', 'b', 'c'))))
    } @@ failing,
    test("isPositive must succeed when number is positive") {
      assertTrue((10) > 0)
    },
    test("isPositive must fail when number is zero") {
      assertTrue((0) > 0)
    } @@ failing,
    test("isPositive must fail when number is negative") {
      assertTrue((-10) > 0)
    } @@ failing,
    test("isRight must succeed when supplied value is Right and satisfy specified assertion") {
      assertTrue((Right(42)).is(_.right) == 42)
    },
    test("isRight must succeed when supplied value is Right") {
      assertTrue((Right(42)).is(_.right))
    },
    test("isSome must succeed when supplied value is Some and satisfy specified assertion") {
      assertTrue((Some("zio")).is(_.some) == "zio")
    },
    test("isSome with isOneOf must succeed when assertion is satisfied") {
      assertTrue((Some("zio")).is(_.some.oneOf(Set("zio", "test"))))
    },
    test("isSome must fail when supplied value is None") {
     assertTrue((None).is(_.some) == "zio")
    } @@ failing,
    test("isSome must succeed when supplied value is Some") {
      assertTrue((Some("zio")).is(_.some.anything))
    },
    test("isSome must fail when supplied value is None") {
      assertTrue((None).is(_.some.anything))
    } @@ failing,
    test("isSorted must succeed when iterable is sorted") {
      assertTrue((Seq(1, 2, 2, 3, 4)).is(_.sorted))
    },
    test("isSorted must succeed for empty iterable") {
      assertTrue((Seq.empty[Int]).is(_.sorted))
    },
    test("isSorted must succeed for singleton iterable") {
      assertTrue((Seq(1)).is(_.sorted))
    },
    test("isSorted must fail when iterable is not sorted") {
      assertTrue((Seq(1, 2, 0, 3, 4)).is(_.sorted))
    } @@ failing,
    test("isSortedReverse must succeed when iterable is reverse sorted") {
      assertTrue((Seq(4, 3, 3, 2, 1)).is(_.sortedReverse))
    },
    test("isSortedReverse must fail when iterable is not reverse sorted") {
      assertTrue((Seq(1, 2, 0, 3, 4)).is(_.sortedReverse))
    } @@ failing,
    test("isSubtype must succeed when value is subtype of specified type") {
      assertTrue((dog).is(_.subtype[Animal].anything))
    },
    test("isSubtype must fail when value is supertype of specified type") {
      assertTrue((animal).is(_.subtype[Cat].anything))
    } @@ failing,
    test("isSubtype must fail when value is neither subtype nor supertype of specified type") {
      assertTrue((cat).is(_.subtype[Dog].anything))
    } @@ failing,
    test("isSubtype must handle primitive types") {
      assertTrue((1).is(_.subtype[Int].anything))
    },
    test("isSubtype must handle malformed class names") {
      sealed trait Exception
      object Exception {
        case class MyException() extends Exception
      }
      val exception = new Exception.MyException
      assertTrue((exception).is(_.subtype[Exception.MyException].anything))
    },
    test("isSuccess must succeed when Success value satisfies the specified assertion") {
      assertTrue((Success(1)).is(_.success).>(0))
    },
    test("isSuccess must succeed when Try value is Success") {
      assertTrue((Success(1)).is(_.success.anything))
    },
    test("isTrue must succeed when supplied value is true") {
      assertTrue((true).==(true))
    },
    test("isUnit must succeed when supplied value is ()") {
      assertTrue((()).==(()))
    },
    testM("isUnit must not compile when supplied value is not ()") {
      val result = typeCheck("assert(10)(isUnit)")
      assertM(result)(isLeft(anything))
    },
    test("isWithin must succeed when supplied value is within range (inclusive)") {
      assertTrue((10).is(_.within(0, 10)))
    },
    test("isWithin must fail when supplied value is out of range") {
      assertTrue((42).is(_.within(0, 10)))
    } @@ failing,
    test("isZero must succeed when number is zero") {
      assertTrue((0).==(0))
    },
    test("isZero must fail when number is not zero") {
      assertTrue((10).==(0))
    } @@ failing,
    test("matches must succeed when the string matches the regex") {
      assertTrue(("(123) 456-7890").matches("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$"))
    },
    test("matches must fail when the string does not match the regex") {
      assertTrue(("456-7890").matches("\\([1-9]{3}\\) [0-9]{3}\\-[0-9]{4}$"))
    } @@ failing,
    test("nonNegative must succeed when number is positive") {
      assertTrue((10).>=(0))
    },
    test("nonNegative must succeed when number is zero") {
      assertTrue((0).>=(0))
    },
    test("nonNegative must fail when number is negative") {
      assertTrue((-10).>=(0))
    } @@ failing,
    test("nonPositive must succeed when number is negative") {
      assertTrue((-10).<=(0))
    },
    test("nonPositive must succeed when number is zero") {
      assertTrue((0).<=(0))
    },
    test("nonPositive must fail when number is positive") {
      assertTrue((10).<=(0))
    } @@ failing,
    test("not must succeed when negation of specified assertion is true") {
      assert(0)(not(equalTo(42)))
    },
    test("nothing must always fail") {
      assert(42)(nothing)
    } @@ failing,
    test("startsWith must succeed when the supplied value starts with the specified sequence") {
      assertTrue((List(1, 2, 3, 4, 5)).startsWith(List(1, 2, 3)))
    },
    test("startsWith must fail when the supplied value does not start with the specified sequence") {
      assertTrue((List(1, 2, 3, 4, 5)).startsWith(List(3, 4, 5)))
    } @@ failing,
    test("startsWithString must succeed when the supplied value starts with the specified string") {
      assertTrue(("zio").startsWith("z"))
    },
    test("startsWithString must fail when the supplied value does not start with the specified string") {
      assertTrue(("zio").startsWith("o"))
    } @@ failing,
    test("succeeds must succeed when supplied value is Exit.succeed and satisfy specified assertion") {
      assertTrue((Exit.succeed("Some Error")).is(_.success) == "Some Error")
    },
    test("succeeds must fail when supplied value is Exit.fail") {
      assertTrue((Exit.fail("Some Error")).is(_.success) == "Some Error")
    } @@ failing,
    test("throws must succeed when given assertion is correct") {
      assertTrue((throw sampleException).is(_.throwing) == sampleException)
    },
    test("hasThrowableCause must succeed when supplied value has matching cause") {
      val cause = new Exception("cause")
      val t     = new Exception("result", cause)
      assertTrue((t).is(_.cause.message) == "cause")
    },
    test("hasThrowableCause must fail when supplied value has non-matching cause") {
      val cause = new Exception("something different")
      val t     = new Exception("result", cause)
      assertTrue((t).is(_.cause.message) == "cause")
    } @@ failing,
    test("hasThrowableCause must fail when supplied value does not have a cause") {
      val t = new Exception("result")
      assertTrue((t).is(_.cause.message) == "cause")
    } @@ failing
  )

  case class SampleUser(name: String, age: Int)
  val sampleUser: SampleUser = SampleUser("User", 42)
  val sampleException        = new Exception

  val someException = new RuntimeException("Boom!")

  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  val animal: Animal = new Animal {}
  val dog: Dog       = new Dog {}
  val cat: Cat       = new Cat {}
}

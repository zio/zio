package examples

import examples.Suites._
import zio.test.{ test, _ }

private object Suites {

  val operationsSuite = suite("Basic Operations")(
    test("Addition operation") {
      assert(1 + 1, Predicate.equals(2))
    },
    test("Subtraction operation") {
      assert(10 - 5, Predicate.equals(5))
    },
    test("Multiplication operation") {
      assert(10 * 2, Predicate.equals(20))
    },
    test("Division operation") {
      assert(25 / 5, Predicate.equals(5))
    },
    test("EQ operation") {
      assert(1 == 1, Predicate.isTrue)
    },
    test("GT operation") {
      assert(10, Predicate.isGreaterThan(11))
    },
    test("GT or EQ operation") {
      assert(10, Predicate.isGreaterThanEqual(10))
    },
    test("LT operation") {
      assert(5, Predicate.isLessThan(4))
    },
    test("LT or EQ operation") {
      assert(5, Predicate.isLessThanEqual(5))
    },
    test("`Between` operation") {
      assert(5, Predicate.isWithin(0, 10))
    }
  )

  val listSuite = suite("Iterable")(
    test("Iterable contains element") {
      assert(List(1, 2, 3), Predicate.contains(1))
    },
    test("Iterable exists element") {
      assert(List('z', 'i', 'o'), Predicate.exists(Predicate.equals('o')))
    },
    test("Iterable forall elements") {
      assert(
        List("zio", "zmanaged", "zstream", "ztrace", "zschedule").map(_.head),
        Predicate.forall(Predicate.equals('z'))
      )
    },
    test("Iterable size") {
      assert(List(1, 2, 3, 4, 5), Predicate.hasSize(Predicate.equals(5)))
    }
  )

  case class User(name: String, email: String)

  sealed trait Semaphore
  case object Green  extends Semaphore
  case object Yellow extends Semaphore
  case object Red    extends Semaphore

  val user = User("John Doe", "johndoe@zio.com")

  val emailPredicate: Predicate[User] =
    Predicate.hasField[User, String]("email", _.email, Predicate.equals("johndoe@zio.com"))

  val patternMatchSuite = suite("Pattern match operations")(
    test("User has a email") {
      assert(user, emailPredicate)
    },
    test("User not exists") {
      assert(Option.empty[User], Predicate.isNone)
    },
    test("User exists and have a name") {
      assert(Some(user), Predicate.isSome(emailPredicate))
    },
    test("Either is left") {
      assert(Left("Failure"), Predicate.isLeft(Predicate.equals("Failure")))
    },
    test("Either is right") {
      assert(Right("Success"), Predicate.isRight(Predicate.equals("Success")))
    },
    test("Yellow is a Semaphore") {
      assert(Yellow, Predicate.isSubtype[Semaphore](Predicate.equals(Yellow)))
    },
    test("Semaphore is not green") {
      assert(Red, Predicate.isSubtype[Semaphore](Predicate.not(Predicate.equals(Green))))
    }
  )

}

object PredicateExampleSpec
    extends DefaultRunnableSpec(
      suite("Predicate examples")(operationsSuite, listSuite, patternMatchSuite)
    )

package examples

import examples.PredicateSuites._
import zio.test.Predicate._
import zio.test.{ test, _ }

private object PredicateSuites {

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
      assert(1 == 1, isTrue)
    },
    test("GT operation") {
      assert(10, isGreaterThan(11))
    },
    test("GT or EQ operation") {
      assert(10, isGreaterThanEqual(10))
    },
    test("LT operation") {
      assert(5, isLessThan(4))
    },
    test("LT or EQ operation") {
      assert(5, isLessThanEqual(5))
    },
    test("`Between` operation") {
      assert(5, isWithin(0, 10))
    }
  )

  val listSuite = suite("Iterable")(
    test("Iterable contains element") {
      assert(List(1, 2, 3), contains(1))
    },
    test("Iterable exists element") {
      assert(List('z', 'i', 'o'), exists(Predicate.equals('o')))
    },
    test("Iterable forall elements") {
      assert(
        List("zio", "zmanaged", "zstream", "ztrace", "zschedule").map(_.head),
        forall(Predicate.equals('z'))
      )
    },
    test("Iterable size") {
      assert(List(1, 2, 3, 4, 5), hasSize(Predicate.equals(5)))
    }
  )

  case class User(name: String, email: String)

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  val user = User("John Doe", "johndoe@zio.com")

  val emailPredicate: Predicate[User] =
    Predicate.hasField[User, String]("email", _.email, Predicate.equals("johndoe@zio.com"))

  val patternMatchSuite = suite("Pattern match operations")(
    test("User has a email") {
      assert(user, emailPredicate)
    },
    test("User not exists") {
      assert(Option.empty[User], isNone)
    },
    test("User exists and have a name") {
      assert(Some(user), isSome(emailPredicate))
    },
    test("Either is left") {
      assert(Left("Failure"), isLeft(Predicate.equals("Failure")))
    },
    test("Either is right") {
      assert(Right("Success"), isRight(Predicate.equals("Success")))
    },
    test("Blue is a Color") {
      assert(Blue, isSubtype[Color](Predicate.equals(Blue)))
    },
    test("Color is not green") {
      assert(Red, isSubtype[Color](Predicate.not(Predicate.equals(Green))))
    },
    test("Option content is `zio` ") {
      val predicate: Predicate[Some[String]] = isCase("Some", Some.unapply, Predicate.equals("zio"))
      assert(Some("zio"), predicate)
    }
  )

  val customPredicatesSuite = suite("Custom predicates")(
    test("String is not empty predicate") {
      def nonEmptyString = predicate[String]("String is not empty") { a =>
        if (!a.isEmpty)
          Assertion.success
        else
          Assertion.failure(())
      }

      assert("Some string", nonEmptyString)
    },
    test("String is empty predicate (direct)") {

      def emptyString = predicate[String]("String is empty") { a =>
        if (a.isEmpty)
          Assertion.success
        else
          Assertion.failure(())
      }

      val predicateDirect = Predicate.predicateDirect[String]("String is empty (direct)") { a =>
        emptyString.run(a)
      }

      assert("", predicateDirect)

    }
  )

  val compositionSuite = suite("Predicates composition")(
    test("List contains an element and have a defined size") {

      val composition = contains(1) && hasSize(Predicate.equals(5))

      assert(List(1, 2, 3, 4, 5), composition)

    },
    test("All elements are Green or the list is empty") {

      val composition = forall(isSome(Predicate.equals(Green))) || hasSize(
        Predicate.equals(0)
      )

      assert(Nil, composition)

    }
  )

}

object PredicateExampleSpec
    extends DefaultRunnableSpec(
      suite("Predicate examples")(
        operationsSuite,
        listSuite,
        patternMatchSuite,
        customPredicatesSuite,
        compositionSuite
      )
    )

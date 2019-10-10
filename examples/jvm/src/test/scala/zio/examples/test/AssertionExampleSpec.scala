package examples

import examples.assertionSuites._
import zio.test.Assertion._
import zio.test._

private object assertionSuites {

  val operationsSuite = suite("Basic Operations")(
    test("Addition operation") {
      assert(1 + 1, equalTo(2))
    },
    test("Subtraction operation") {
      assert(10 - 5, equalTo(5))
    },
    test("Multiplication operation") {
      assert(10 * 2, equalTo(20))
    },
    test("Division operation") {
      assert(25 / 5, equalTo(5))
    },
    test("EQ operation") {
      assert(1 == 1, isTrue)
    },
    test("GT operation") {
      assert(10, isGreaterThan(9))
    },
    test("GT or EQ operation") {
      assert(10, isGreaterThanEqualTo(10))
    },
    test("LT operation") {
      assert(5, isLessThan(6))
    },
    test("LT or EQ operation") {
      assert(5, isLessThanEqualTo(5))
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
      assert(List('z', 'i', 'o'), exists(equalTo('o')))
    },
    test("Iterable forall elements") {
      assert(
        List("zio", "zmanaged", "zstream", "ztrace", "zschedule").map(_.head),
        forall(equalTo('z'))
      )
    },
    test("Iterable size") {
      assert(List(1, 2, 3, 4, 5), hasSize(equalTo(5)))
    }
  )

  case class User(name: String, email: String)

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  val user = User("John Doe", "johndoe@zio.com")

  val emailassertion: Assertion[User] =
    hasField[User, String]("email", _.email, equalTo("johndoe@zio.com"))

  val patternMatchSuite = suite("Pattern match operations")(
    test("User has a email") {
      assert(user, emailassertion)
    },
    test("User not exists") {
      assert(Option.empty[User], isNone)
    },
    test("User exists and have a name") {
      assert(Some(user), isSome(emailassertion))
    },
    test("Either is left") {
      assert(Left("Failure"), isLeft(equalTo("Failure")))
    },
    test("Either is right") {
      assert(Right("Success"), isRight(equalTo("Success")))
    },
    test("Blue is a Color") {
      assert(Blue, isSubtype[Color](equalTo(Blue)))
    },
    test("Color is not green") {
      assert(Red, isSubtype[Color](not(equalTo(Green))))
    },
    test("Option content is `zio` ") {
      val assertion: Assertion[Some[String]] = isCase("Some", Some.unapply, equalTo("zio"))
      assert(Some("zio"), assertion)
    }
  )

  val customAssertionsSuite = suite("Custom Assertions")(
    test("String is not empty") {

      def nonEmptyString: Assertion[String] = assertion[String]("String is not empty")()(_.nonEmpty)

      assert[String]("Some String", nonEmptyString)

    },
    test("String is empty assertion (direct)") {

      def emptyString = assertion[String]("String is empty")()(_.isEmpty)

      val predicateDirect: Assertion[String] = assertionDirect[String]("String is empty (direct)")()(emptyString)

      assert("", predicateDirect)

    },
    test("Coproduct values are not empty (direct)") {

      def nonEmptyString = assertion[String]("String is nonEmpty")()(_.nonEmpty)

      sealed trait StringOrList

      case class Str(value: String)           extends StringOrList
      case class StrList(value: List[String]) extends StringOrList

      val predicateDirect: Assertion[StringOrList] =
        assertionDirect[StringOrList]("StringOrList is nonEmpty (direct)")() {
          case Str(value)         => nonEmptyString(value)
          case StrList(listValue) => nonEmptyString(listValue.mkString(""))
        }

      assert(Str("some value"), predicateDirect) && assert(StrList(List("", "some", "value", "")), predicateDirect)

    }
  )

  val compositionSuite = suite("Assertions composition")(
    test("List contains an element and have a defined size") {

      val composition = contains(1) && hasSize(equalTo(5))

      assert(List(1, 2, 3, 4, 5), composition)

    },
    test("All elements are Green or the list is empty") {

      val composition = forall(isSome(equalTo(Green))) || hasSize(
        equalTo(0)
      )

      assert(Nil, composition)

    }
  )

}

object assertionExampleSpec
    extends DefaultRunnableSpec(
      suite("Assertion examples")(
        operationsSuite,
        listSuite,
        patternMatchSuite,
        customAssertionsSuite,
        compositionSuite
      )
    )

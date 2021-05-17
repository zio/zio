package zio.test

import zio.test.AssertionSyntax.EitherAssertionOps
import zio.test.SmartTestTypes._
import zio.test.TestAspect.ignore

import java.time.LocalDateTime

/**
 * - Scala 3
 * - Refactor to make Scala 3 easy.
 * - Create some data structure that allows us to list out
 *   - Method Name, Method
 * - Fix up rendering issues.
 *   √ Do not list value for literals
 *   √ Add spaces around infix with code show.
 *   √ Special case apply.
 *   √ Use the actual written showCode(expr) for the withField for the macro code
 *   - Fix IsConstructor
 * √ Improve rendering for all the existing assertions
 * √ conjunction/disjunction/negation, break apart at top level in macro.
 * √ handle exceptions thrown by methods in `assert`
 * √ Add a prose, human-readable error message for assertions (instead of 'does not satisfy hasAt(0)')
 * - Add all the methods we want
 *   - right.get (on an Either)
 *   - toOption.get (on an Either)
 *   - forall
 * - Diff Stuff. Strings, case classes, maps, anything. User customizable.
 * - Exposing bugs. try to break in as many ways as possible, and add helpful error messages
 *   √ how to handle multi-statement blocks
 */

object SmartAssertionSpec extends ZIOBaseSpec {

  val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  def spec: ZSpec[Environment, Failure] = suite("SmartAssertionSpec")(
    test("Head") {
      assert(!(Array(1, 8, 2, 3, 888)(0) == 1))
    },
    test("missing element") {
      assert(company.users(8).posts.exists(_.title == "hi"))
    },
    test("fails predicate") {
      assert(company.users.head.posts.exists(_.title == "hii"))
    },
    test("nested access") {
      val company = Company("Cool Company", List.empty)
      assert(company.users.head.posts.exists(_.title == "hii"))
    },
    test("boolean method") {
      assert(company.users.head.posts.head.publishDate.isDefined)
    },
    test("boolean method with args") {
      assert(company.users.head.posts.head.publishDate.contains(LocalDateTime.MAX))
    },
    test("forall") {
      val list = List(10, 5, 8, 3, 4)
      assert(list.forall(_ % 2 == 0))
    },
    test("right.get") {
      val myEither: Either[String, Int] = Left("string")
      case class Cool(int: Int)
      assert(myEither.right.get + 1 > 18)
    },
    test("string contains") {
      val myString = "something"
      assert(myString.contains("aoseunoth") && myString == "coool")
    },
    suite("referencing literals")(
      test("List") {
        assert((List(10, 23, 8, 8) intersect List(23)).head + 31 == 3)
      },
      test("Case Class") {
        assert(Company("Nice", List.empty).name.contains("aoeu"))
      },
      test("Array") {
        assert(Array(1, 2, 3, 9, 8).head == 3)
      },
      test("Object constructor") {
        assert(zio.duration.Duration.fromNanos(1000) == zio.duration.Duration.Zero)
      }
    ),
    suite("contains")(
      test("Option") {
        assert(company.users.head.posts.head.publishDate.contains(LocalDateTime.MAX))
      }
    ),
    suite("Either")(
      test("right.get") {
        val myEither: Either[String, Int] = Left("string")
        assert(myEither.right.get + 1 > 11233)
      },
      test("$asRight") {
        val myEither: Either[String, Int] = Left("string")
        assert(myEither.$asRight + 1 > 11233)
      },
      test("toOption.get") {
        val myEither: Either[String, Int] = Left("string")
        assert(myEither.toOption.get + 1 > 11238)
      }
    ),
    suite("Helpers")(
      suite("$as")(
        test("success") {
          val someColor: Color = Red("hello")
          assert(someColor.$as[Red].name == "cool")
        },
        test("fail") {
          val someColor: Color = Red("hello")
          assert(someColor.$as[Blue].brightness > 38)
        }
      ),
      test("asInstanceOf") {
        val someColor: Color = Red("hello")
        case class Bomb(name: String) {
          def getName: String = throw new Error("SPLODE")
        }
        val bomb = Bomb("boomy")
        assert(bomb.getName.contains("HIII"))
      },
      test("asInstanceOf") {
        val someColor: Color = Red("hello")
        assert(someColor.asInstanceOf[Blue].brightness > 39)
      }
    )
  ) // @@ failing

}

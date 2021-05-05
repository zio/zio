package zio.test

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
  case class Post(title: String, publishDate: Option[LocalDateTime] = None)
  case class User(name: String, posts: List[Post])
  case class Company(name: String, users: List[User])

  val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  def spec: ZSpec[Environment, Failure] = suite("SmartAssertionSpec")(
    test("OH") {
      assert(!(Array(1, 8, 2, 3, 88)(0) == 1))
    },
    test("missing element") {
      assert(company.users(8).posts.exists(_.title == "hi"))
    },
    test("fails predicate") {
      assert(company.users.head.posts.exists(_.title == "hi"))
    },
    test("nested access") {
      val company = Company("Cool Company", List.empty)
      assert(company.users.head.posts.exists(_.title == "hi"))
    },
    test("get") {
      assert(company.users.head.posts.head.publishDate.isDefined)
    },
    test("right.get") {
      val myEither: Either[String, Int] = Left("string")
      case class Cool(int: Int)
      assert(myEither.right.get + 1 > 18)
    },
    test("string contains") {
      val myString = "something"
      assert(myString.contains("nice"))
    },
    test("not equal") {
      assert((List(10, 23, 8, 8) intersect List(23)).head + 31 == 3)
    },
    test("not equal") {
      assert(Company("Niceeee", List.empty).name.isBlank)
    },
    test("array") {
      assert(Array(1, 2, 3, 8).head == 3)
    }
  )
}

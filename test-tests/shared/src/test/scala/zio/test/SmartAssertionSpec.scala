package zio.test

import zio.test.SmartTestTypes._
import zio.test.TestAspect.failing

import java.time.LocalDateTime

object SmartAssertionSpec extends ZIOBaseSpec {

  val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  def spec: ZSpec[Environment, Failure] = suite("SmartAssertionSpec")(
    test("Head") {
      val array = Array(1, 8, 2, 3, 888)
      assertTrue(
        !(array(0) == 1),
        array(3) == 10,
        array(1) < 2
      )
    },
    test("missing element") {
      assertTrue(company.users(8).posts.exists(_.title == "hi"))
    },
    test("fails predicate") {
      assertTrue(company.users.head.posts.exists(_.title == "hii"))
    },
    test("nested access") {
      val company = Company("Cool Company", List.empty)
      assertTrue(company.users.head.posts.exists(_.title == "hii"))
    },
    test("boolean method") {
      assertTrue(company.users.head.posts.head.publishDate.isDefined)
    },
    test("boolean method with args") {
      assertTrue(company.users.head.posts.head.publishDate.contains(LocalDateTime.MAX))
    },
    test("forall") {
      val list = List(10, 5, 8, 3, 4)
      assertTrue(list.forall(_ % 2 == 0))
    },
    test("right.get") {
      val myEither: Either[String, Int] = Left("string")
      case class Cool(int: Int)
      assertTrue(myEither.right.get + 1 > 18)
    },
    test("string contains") {
      val myString = "something"
      assertTrue(myString.contains("aoseunoth") && myString == "coool")
    },
    suite("referencing literals")(
      test("List") {
        val list = List(10, 23, 8, 8)
        assertTrue((list intersect List(23)).head + 31 == 3)
      },
      test("Case Class") {
        assertTrue(Company("Nice", List.empty).name.contains("aoeu"))
      },
      test("Array") {
        val array = Array(1, 2, 3, 9, 8)
        assertTrue(array.head == 3)
      },
      test("Object constructor") {
        assertTrue(zio.duration.Duration.fromNanos(1000) == zio.duration.Duration.Zero)
      }
    ),
    suite("contains")(
      test("Option") {
        assertTrue(company.users.head.posts.head.publishDate.contains(LocalDateTime.MAX))
      }
    ),
    suite("Either")(
      test("right.get") {
        val myEither: Either[String, Int] = Left("string")
        assertTrue(myEither.right.get + 1 > 11233)
      }
    ),
    suite("Exceptions")(
      test("throws") {
        case class Bomb(name: String) {
          def getName: String = throw new Error("SPLODE")
        }
        val bomb = Bomb("boomy")
        assertTrue(bomb.getName.contains("HIII"))
      }
    )
  ) @@ failing
}

package zio.test

import zio.test.AssertionSyntax.EitherAssertionOps
import zio.test.SmartTestTypes._
import zio.test.TestAspect.failing

import java.time.LocalDateTime

object SmartAssertionSpec extends ZIOBaseSpec {

  val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  def spec: ZSpec[Environment, Failure] = suite("SmartAssertionSpec")(
    test("Head") {
      val array = Array(1, 8, 2, 3, 888)
      assertTrue(!(array(0) == 1))
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
      },
      test("$asRight") {
        val myEither: Either[String, Int] = Left("string")
        assertTrue(myEither.$asRight + 1 > 11233)
      }
    ),
    suite("Helpers")(
      suite("$as")(
        test("success") {
          val someColor: Color = Red("hello")
          assertTrue(someColor.$as[Red].name == "cool")
        },
        test("fail") {
          val someColor: Color = Red("hello")
          assertTrue(someColor.$as[Blue].brightness > 38)
        }
      ),
      test("asInstanceOf") {
//        val someColor: Color = Red("hello")
        case class Bomb(name: String) {
          def getName: String = throw new Error("SPLODE")
        }
        val bomb = Bomb("boomy")
        assertTrue(bomb.getName.contains("HIII"))
      },
      test("asInstanceOf") {
        val someColor: Color = Red("hello")
        assertTrue(someColor.asInstanceOf[Blue].brightness > 39)
      }
    )
  ) @@ failing

}

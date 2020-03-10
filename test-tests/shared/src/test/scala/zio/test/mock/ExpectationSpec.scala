package zio.test.mock

import zio.test.Assertion
import zio.test.mock.module.ModuleMock
import zio.test.{ assert, suite, test, ZIOBaseSpec }

object ExpectationSpec extends ZIOBaseSpec with MockSpecUtils {

  import Assertion._
  import Expectation._

  lazy val A = ModuleMock.SingleParam(equalTo(1)) returns value("foo")
  lazy val B = ModuleMock.Static returns value("bar")
  lazy val C = ModuleMock.Looped(equalTo(1)) returns never

  def spec = suite("ExpectationSpec")(
    suite("and")(
      test("A and B")(assert(A and B)(equalTo(And(A :: B :: Nil, ModuleMock.mock)))),
      test("A && B")(assert(A && B)(equalTo(And(A :: B :: Nil, ModuleMock.mock)))),
      test("associativity")(assert((A and B) and C)(equalTo(A and (B and C))))
    ),
    suite("andThen")(
      test("A andThen B")(assert(A andThen B)(equalTo(Chain(A :: B :: Nil, ModuleMock.mock)))),
      test("A ++ B")(assert(A ++ B)(equalTo(Chain(A :: B :: Nil, ModuleMock.mock)))),
      test("associativity")(assert((A andThen B) andThen C)(equalTo(A andThen (B andThen C))))
    ),
    suite("or")(
      test("A or B")(assert(A or B)(equalTo(Or(A :: B :: Nil, ModuleMock.mock)))),
      test("A || B")(assert(A || B)(equalTo(Or(A :: B :: Nil, ModuleMock.mock)))),
      test("associativity")(assert((A or B) or C)(equalTo(A or (B or C))))
    ),
    suite("repeats")(
      test("A repeats (2 to 5)")(assert(A repeats (2 to 5))(equalTo(Repeated(A, 2 to 5)))),
      test("nested repeats")(assert(A.repeats(2 to 3).repeats(1 to 2))(equalTo(Repeated(Repeated(A, 2 to 3), 1 to 2))))
    )
  )
}

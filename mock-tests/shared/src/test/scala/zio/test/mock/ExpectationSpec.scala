package zio.mock

import zio.mock.module.{PureModule, PureModuleMock}
import zio.test._

object ExpectationSpec extends ZIOBaseSpec {

  import Assertion._
  import Expectation._
  import PureModuleMock._

  lazy val A: Expectation[PureModule] = SingleParam(equalTo(1), value("foo"))
  lazy val B: Expectation[PureModule] = Static(value("bar"))
  lazy val C: Expectation[PureModule] = Looped(equalTo(1), never)

  private def isAnd[R](children: List[Expectation[_]]) =
    isSubtype[And[R]](
      hasField[And[R], List[Expectation[R]]](
        "children",
        _.children,
        equalTo(children.asInstanceOf[List[Expectation[R]]])
      )
    )

  private def isChain[R](children: List[Expectation[_]]) =
    isSubtype[Chain[R]](
      hasField[Chain[R], List[Expectation[R]]](
        "children",
        _.children,
        equalTo(children.asInstanceOf[List[Expectation[R]]])
      )
    )

  private def isOr[R](children: List[Expectation[_]]) =
    isSubtype[Or[R]](
      hasField[Or[R], List[Expectation[R]]](
        "children",
        _.children,
        equalTo(children.asInstanceOf[List[Expectation[R]]])
      )
    )

  def spec = suite("ExpectationSpec")(
    suite("and")(
      test("A and B")(assert(A and B)(isAnd(A :: B :: Nil))),
      test("A && B")(assert(A && B)(isAnd(A :: B :: Nil)))
    ),
    suite("andThen")(
      test("A andThen B")(assert(A andThen B)(isChain(A :: B :: Nil))),
      test("A ++ B")(assert(A ++ B)(isChain(A :: B :: Nil)))
    ),
    suite("or")(
      test("A or B")(assert(A or B)(isOr(A :: B :: Nil))),
      test("A || B")(assert(A || B)(isOr(A :: B :: Nil)))
    ),
    suite("repeats")(
      test("A repeats (2 to 5)")(assert(A repeats (2 to 5))(equalTo(Repeated(A, 2 to 5)))),
      test("nested repeats")(assert(A.repeats(2 to 3).repeats(1 to 2))(equalTo(Repeated(Repeated(A, 2 to 3), 1 to 2))))
    ),
    suite("optional")(
      test("A optional")(assert(A.optional)(equalTo(Repeated(A, 0 to 1))))
    ),
    suite("exactly and derived")(
      test("A exactly 5")(assert(A exactly 5)(equalTo(Exactly(A, 5)))),
      test("A twice")(assert(A.twice)(equalTo(Exactly(A, 2)))),
      test("A thrice")(assert(A.thrice)(equalTo(Exactly(A, 3))))
    )
  )
}

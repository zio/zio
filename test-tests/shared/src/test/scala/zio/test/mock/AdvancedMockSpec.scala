package zio.test.mock

import zio.ZIO
import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.test.mock.module.{ Module, ModuleMock }
import zio.test.{ suite, Assertion, ZIOBaseSpec }

object AdvancedMockSpec extends ZIOBaseSpec with MockSpecUtils {

  import Assertion._
  import Expectation._
  import InvalidCall._
  import MockException._

  val cmdA = ModuleMock.SingleParam
  val cmdB = ModuleMock.Overloaded._0
  val cmdC = ModuleMock.ZeroParams

  val A = ModuleMock.SingleParam(equalTo(1), value("A"))
  val B = ModuleMock.Overloaded._0(equalTo(2), value("B"))
  val C = ModuleMock.ZeroParams(value("C"))

  val a = Module.singleParam(1)
  val b = Module.overloaded(2)
  val c = Module.zeroParams

  type E = InvalidCallException
  type L = List[InvalidCall]

  def hasFailedMatches[T <: InvalidCall](failedMatches: T*): Assertion[Throwable] = {
    val zero = hasSize(equalTo(failedMatches.length))
    isSubtype[E](
      hasField[E, L]("failedMatches", _.failedMatches, failedMatches.zipWithIndex.foldLeft[Assertion[L]](zero) {
        case (acc, (failure, idx)) => acc && hasAt(idx)(equalTo(failure))
      })
    )
  }

  def hasUnexpectedCall[I, E, A](method: Method[Module, I, E, A], args: I): Assertion[Throwable] =
    isSubtype[UnexpectedCallExpection[Module, I, E, A]](
      hasField[UnexpectedCallExpection[Module, I, E, A], Method[Module, I, E, A]]("method", _.method, equalTo(method)) &&
        hasField[UnexpectedCallExpection[Module, I, E, A], Any]("args", _.args, equalTo(args))
    )

  def hasUnsatisfiedExpectations: Assertion[Throwable] =
    isSubtype[UnsatisfiedExpectationsException[Module]](anything)

  def spec = suite("AdvancedMockSpec")(
    suite("expectations composition")(
      suite("A and B")(
        testSpec("A->B passes")(A && B, a *> b, equalTo("B")),
        testSpec("B->A passes")(A && B, b *> a, equalTo("A")),
        testSpecDied("A->A->B fails")(A && B, a *> a *> b, hasFailedMatches(InvalidMethod(cmdA, cmdB, equalTo(2)))),
        testSpecDied("B->B->A fails")(A && B, b *> b *> a, hasFailedMatches(InvalidMethod(cmdB, cmdA, equalTo(1)))),
        testSpecDied("A->C->B fails")(A && B, b *> c *> b, hasFailedMatches(InvalidMethod(cmdC, cmdA, equalTo(1))))
      ),
      suite("A and B and C")(
        testSpec("A->B->C passes")(A && B && C, a *> b *> c, equalTo("C")),
        testSpec("A->C->B passes")(A && B && C, a *> c *> b, equalTo("B")),
        testSpec("B->A->C passes")(A && B && C, b *> a *> c, equalTo("C")),
        testSpec("B->C->A passes")(A && B && C, b *> c *> a, equalTo("A")),
        testSpec("C->A->B passes")(A && B && C, c *> a *> b, equalTo("B")),
        testSpec("C->B->A passes")(A && B && C, c *> b *> a, equalTo("A"))
      ),
      suite("A andThen B")(
        testSpec("A->B passes")(A ++ B, a *> b, equalTo("B")),
        testSpecDied("B->A fails")(A ++ B, b *> a, hasFailedMatches(InvalidMethod(cmdB, cmdA, equalTo(1))))
      ),
      suite("A andThen B andThen C")(
        testSpec("A->B->C passes")(A ++ B ++ C, a *> b *> c, equalTo("C")),
        testSpecDied("A->C->B fails")(
          A ++ B ++ C,
          a *> c *> b,
          hasFailedMatches(InvalidMethod(cmdC, cmdB, equalTo(2)))
        ),
        testSpecDied("B->A->C fails")(
          A ++ B ++ C,
          b *> a *> c,
          hasFailedMatches(InvalidMethod(cmdB, cmdA, equalTo(1)))
        ),
        testSpecDied("B->C->A fails")(
          A ++ B ++ C,
          b *> c *> a,
          hasFailedMatches(InvalidMethod(cmdB, cmdA, equalTo(1)))
        ),
        testSpecDied("C->A->B fails")(
          A ++ B ++ C,
          c *> a *> b,
          hasFailedMatches(InvalidMethod(cmdC, cmdA, equalTo(1)))
        ),
        testSpecDied("C->B->A fails")(A ++ B ++ C, c *> b *> a, hasFailedMatches(InvalidMethod(cmdC, cmdA, equalTo(1))))
      ),
      suite("A or B")(
        testSpec("A passes")(A || B, a, equalTo("A")),
        testSpec("B passes")(A || B, b, equalTo("B")),
        testSpecDied("C fails")(
          A || B,
          c,
          hasFailedMatches(InvalidMethod(cmdC, cmdB, equalTo(2)), InvalidMethod(cmdC, cmdA, equalTo(1)))
        )
      ),
      suite("A or B or C")(
        testSpec("A passes")(A || B || C, a, equalTo("A")),
        testSpec("B passes")(A || B || C, b, equalTo("B")),
        testSpec("C passes")(A || B || C, c, equalTo("C"))
      ), {
        val expectation = A repeats (1 to 3)

        suite("A repeats (1 to 3)")(
          testSpec("1xA passes")(expectation, a, equalTo("A")),
          testSpec("2xA passes")(expectation, a *> a, equalTo("A")),
          testSpec("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testSpecDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
          testSpecDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1))
        )
      }, {
        val expectation = A repeats (1 to 2) repeats (1 to 2)

        suite("A repeats (1 to 2) repeats (1 to 2)")(
          testSpec("1xA passes")(expectation, a, equalTo("A")),
          testSpec("2xA passes")(expectation, a *> a, equalTo("A")),
          testSpec("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testSpec("4xA passes")(expectation, a *> a *> a *> a, equalTo("A")),
          testSpecDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1))
        )
      }, {
        val expectation = A atMost 3

        suite("A atMost 3")(
          testSpec("0xA passes")(expectation, ZIO.unit, isUnit),
          testSpec("1xA passes")(expectation, a, equalTo("A")),
          testSpec("2xA passes")(expectation, a *> a, equalTo("A")),
          testSpec("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testSpecDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
          testSpecDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1))
        )
      }, {
        val expectation = A atLeast 3

        suite("A atLeast 3")(
          testSpecDied("0xA fails")(expectation, ZIO.unit, hasUnsatisfiedExpectations),
          testSpecDied("1xA fails")(expectation, a, hasUnsatisfiedExpectations),
          testSpecDied("2xA fails")(expectation, a *> a, hasUnsatisfiedExpectations),
          testSpec("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testSpec("4xA passes")(expectation, a *> a *> a *> a, equalTo("A")),
          testSpec("5xA passes")(expectation, a *> a *> a *> a *> a, equalTo("A"))
        )
      }, {
        val expectation = (A atLeast 3) andThen A

        suite("atLeast is greedy")(
          suite("(A atLeast 3) andThen A")(
            testSpecDied("0xA fails")(expectation, ZIO.unit, hasUnsatisfiedExpectations),
            testSpecDied("1xA fails")(expectation, a, hasUnsatisfiedExpectations),
            testSpecDied("2xA fails")(expectation, a *> a, hasUnsatisfiedExpectations),
            testSpecDied("3xA fails")(expectation, a *> a *> a, hasUnsatisfiedExpectations),
            testSpecDied("4xA fails")(expectation, a *> a *> a *> a, hasUnsatisfiedExpectations),
            testSpecDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnsatisfiedExpectations)
          )
        )
      }, {
        val expectation = A.optional

        suite("A.optional")(
          testSpec("0xA passes")(expectation, ZIO.unit, isUnit),
          testSpec("1xA passes")(expectation, a, equalTo("A")),
          testSpecDied("2xA fails")(expectation, a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
          testSpecDied("3xA fails")(expectation, a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
          testSpecDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
          testSpecDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1))
        )
      }, {
        val expectation = A.optional andThen A

        suite("optional is greedy")(
          suite("A.optional andThen A")(
            testSpecDied("0xA fails")(expectation, ZIO.unit, hasUnsatisfiedExpectations),
            testSpecDied("1xA fails")(expectation, a, hasUnsatisfiedExpectations),
            testSpec("2xA passes")(expectation, a *> a, equalTo("A")),
            testSpecDied("3xA fails")(expectation, a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
            testSpecDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1)),
            testSpecDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnexpectedCall(ModuleMock.SingleParam, 1))
          )
        )
      }, {
        val expectation = ((A or B) andThen C) repeats (1 to 2)

        suite("((A or B) andThen C) repeats (1 to 2)")(
          testSpec("A->C passes")(expectation, a *> c, equalTo("C")),
          testSpec("B->C passes")(expectation, b *> c, equalTo("C")),
          testSpecDied("A->C->A fails")(expectation, a *> c *> a, hasUnsatisfiedExpectations),
          testSpecDied("A->C->B fails")(expectation, a *> c *> a, hasUnsatisfiedExpectations),
          testSpecDied("B->C->A fails")(expectation, b *> c *> a, hasUnsatisfiedExpectations),
          testSpecDied("B->C->B fails")(expectation, b *> c *> a, hasUnsatisfiedExpectations),
          testSpec("A->C->A->C passes")(expectation, a *> c *> a *> c, equalTo("C")),
          testSpec("A->C->B->C passes")(expectation, a *> c *> b *> c, equalTo("C")),
          testSpec("B->C->A->C passes")(expectation, b *> c *> a *> c, equalTo("C")),
          testSpec("B->C->B->C passes")(expectation, b *> c *> b *> c, equalTo("C")),
          testSpecDied("A->C->A->C->A fails")(
            expectation,
            a *> c *> a *> c *> a,
            hasUnexpectedCall(ModuleMock.SingleParam, 1)
          ),
          testSpecDied("A->C->B->C->A fails")(
            expectation,
            a *> c *> b *> c *> a,
            hasUnexpectedCall(ModuleMock.SingleParam, 1)
          ),
          testSpecDied("B->C->A->C->A fails")(
            expectation,
            b *> c *> a *> c *> a,
            hasUnexpectedCall(ModuleMock.SingleParam, 1)
          ),
          testSpecDied("B->C->B->C->A fails")(
            expectation,
            b *> c *> b *> c *> a,
            hasUnexpectedCall(ModuleMock.SingleParam, 1)
          )
        )
      }
    )
  )
}

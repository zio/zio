package zio.test.mock

import zio.ZIO
import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.test.mock.module.{ PureModule, PureModuleMock }
import zio.test.{ suite, Assertion, Spec, TestFailure, TestSuccess, ZIOBaseSpec }

object AdvancedEffectMockSpec extends ZIOBaseSpec with MockSpecUtils[PureModule] {

  import Assertion._
  import Expectation._
  import InvalidCall._
  import MockException._

  val cmdA = PureModuleMock.SingleParam
  val cmdB = PureModuleMock.Overloaded._0
  val cmdC = PureModuleMock.ZeroParams

  val A: Expectation[PureModule] = PureModuleMock.SingleParam(equalTo(1), value("A"))
  val B: Expectation[PureModule] = PureModuleMock.Overloaded._0(equalTo(2), value("B"))
  val C: Expectation[PureModule] = PureModuleMock.ZeroParams(value("C"))

  val a: ZIO[PureModule, String, String] = PureModule.singleParam(1)
  val b: ZIO[PureModule, String, String] = PureModule.overloaded(2)
  val c                                  = PureModule.zeroParams

  type E = InvalidCallException
  type L = List[InvalidCall]

  def hasFailedMatches[T <: InvalidCall](failedMatches: T*): Assertion[Throwable] = {
    val zero = hasSize(equalTo(failedMatches.length))
    isSubtype[E](
      hasField[E, L](
        "failedMatches",
        _.failedMatches,
        failedMatches.zipWithIndex.foldLeft[Assertion[L]](zero) { case (acc, (failure, idx)) =>
          acc && hasAt(idx)(equalTo(failure))
        }
      )
    )
  }

  def hasUnexpectedCall[I, E, A](capability: Capability[PureModule, I, E, A], args: I): Assertion[Throwable] =
    isSubtype[UnexpectedCallException[PureModule, I, E, A]](
      hasField[UnexpectedCallException[PureModule, I, E, A], Capability[PureModule, I, E, A]](
        "capability",
        _.capability,
        equalTo(capability)
      ) &&
        hasField[UnexpectedCallException[PureModule, I, E, A], Any]("args", _.args, equalTo(args))
    )

  def hasUnsatisfiedExpectations: Assertion[Throwable] =
    isSubtype[UnsatisfiedExpectationsException[PureModule]](anything)

  def spec: Spec[Any, TestFailure[Any], TestSuccess] = suite("AdvancedEffectMockSpec")(
    suite("expectations composition")(
      suite("A and B")(
        testValue("A->B passes")(A && B, a *> b, equalTo("B")),
        testValue("B->A passes")(A && B, b *> a, equalTo("A")),
        testDied("A->A->B fails")(A && B, a *> a *> b, hasFailedMatches(InvalidCapability(cmdA, cmdB, equalTo(2)))),
        testDied("B->B->A fails")(A && B, b *> b *> a, hasFailedMatches(InvalidCapability(cmdB, cmdA, equalTo(1)))),
        testDied("A->C->B fails")(A && B, b *> c *> b, hasFailedMatches(InvalidCapability(cmdC, cmdA, equalTo(1))))
      ),
      suite("A and B and C")(
        testValue("A->B->C passes")(A && B && C, a *> b *> c, equalTo("C")),
        testValue("A->C->B passes")(A && B && C, a *> c *> b, equalTo("B")),
        testValue("B->A->C passes")(A && B && C, b *> a *> c, equalTo("C")),
        testValue("B->C->A passes")(A && B && C, b *> c *> a, equalTo("A")),
        testValue("C->A->B passes")(A && B && C, c *> a *> b, equalTo("B")),
        testValue("C->B->A passes")(A && B && C, c *> b *> a, equalTo("A"))
      ),
      suite("A andThen B")(
        testValue("A->B passes")(A ++ B, a *> b, equalTo("B")),
        testDied("B->A fails")(A ++ B, b *> a, hasFailedMatches(InvalidCapability(cmdB, cmdA, equalTo(1))))
      ),
      suite("A andThen B andThen C")(
        testValue("A->B->C passes")(A ++ B ++ C, a *> b *> c, equalTo("C")),
        testDied("A->C->B fails")(
          A ++ B ++ C,
          a *> c *> b,
          hasFailedMatches(InvalidCapability(cmdC, cmdB, equalTo(2)))
        ),
        testDied("B->A->C fails")(
          A ++ B ++ C,
          b *> a *> c,
          hasFailedMatches(InvalidCapability(cmdB, cmdA, equalTo(1)))
        ),
        testDied("B->C->A fails")(
          A ++ B ++ C,
          b *> c *> a,
          hasFailedMatches(InvalidCapability(cmdB, cmdA, equalTo(1)))
        ),
        testDied("C->A->B fails")(
          A ++ B ++ C,
          c *> a *> b,
          hasFailedMatches(InvalidCapability(cmdC, cmdA, equalTo(1)))
        ),
        testDied("C->B->A fails")(A ++ B ++ C, c *> b *> a, hasFailedMatches(InvalidCapability(cmdC, cmdA, equalTo(1))))
      ),
      suite("A or B")(
        testValue("A passes")(A || B, a, equalTo("A")),
        testValue("B passes")(A || B, b, equalTo("B")),
        testDied("C fails")(
          A || B,
          c,
          hasFailedMatches(InvalidCapability(cmdC, cmdB, equalTo(2)), InvalidCapability(cmdC, cmdA, equalTo(1)))
        )
      ),
      suite("A or B or C")(
        testValue("A passes")(A || B || C, a, equalTo("A")),
        testValue("B passes")(A || B || C, b, equalTo("B")),
        testValue("C passes")(A || B || C, c, equalTo("C"))
      ),
      suite("(A andThen B) or (B andThen A)")(
        testValue("A->B passes")((A ++ B) || (B ++ A), a *> b, equalTo("B")),
        testValue("B->A passes")((A ++ B) || (B ++ A), b *> a, equalTo("A"))
      ), {
        val expectation = A repeats (1 to 3)

        suite("A repeats (1 to 3)")(
          testValue("1xA passes")(expectation, a, equalTo("A")),
          testValue("2xA passes")(expectation, a *> a, equalTo("A")),
          testValue("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
          testDied("5xA fails")(
            expectation,
            a *> a *> a *> a *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          )
        )
      }, {
        val expectation = A repeats (1 to 2) repeats (1 to 2)

        suite("A repeats (1 to 2) repeats (1 to 2)")(
          testValue("1xA passes")(expectation, a, equalTo("A")),
          testValue("2xA passes")(expectation, a *> a, equalTo("A")),
          testValue("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testValue("4xA passes")(expectation, a *> a *> a *> a, equalTo("A")),
          testDied("5xA fails")(
            expectation,
            a *> a *> a *> a *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          )
        )
      }, {
        val expectation = A atMost 3

        suite("A atMost 3")(
          testValue("0xA passes")(expectation, ZIO.unit, isUnit),
          testValue("1xA passes")(expectation, a, equalTo("A")),
          testValue("2xA passes")(expectation, a *> a, equalTo("A")),
          testValue("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
          testDied("5xA fails")(
            expectation,
            a *> a *> a *> a *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          )
        )
      }, {
        val expectation = A atLeast 3

        suite("A atLeast 3")(
          testDied("0xA fails")(expectation, ZIO.unit, hasUnsatisfiedExpectations),
          testDied("1xA fails")(expectation, a, hasUnsatisfiedExpectations),
          testDied("2xA fails")(expectation, a *> a, hasUnsatisfiedExpectations),
          testValue("3xA passes")(expectation, a *> a *> a, equalTo("A")),
          testValue("4xA passes")(expectation, a *> a *> a *> a, equalTo("A")),
          testValue("5xA passes")(expectation, a *> a *> a *> a *> a, equalTo("A"))
        )
      }, {
        val expectation = (A atLeast 3) andThen A

        suite("atLeast is greedy")(
          suite("(A atLeast 3) andThen A")(
            testDied("0xA fails")(expectation, ZIO.unit, hasUnsatisfiedExpectations),
            testDied("1xA fails")(expectation, a, hasUnsatisfiedExpectations),
            testDied("2xA fails")(expectation, a *> a, hasUnsatisfiedExpectations),
            testDied("3xA fails")(expectation, a *> a *> a, hasUnsatisfiedExpectations),
            testDied("4xA fails")(expectation, a *> a *> a *> a, hasUnsatisfiedExpectations),
            testDied("5xA fails")(expectation, a *> a *> a *> a *> a, hasUnsatisfiedExpectations)
          )
        )
      }, {
        val expectation = A.optional

        suite("A.optional")(
          testValue("0xA passes")(expectation, ZIO.unit, isUnit),
          testValue("1xA passes")(expectation, a, equalTo("A")),
          testDied("2xA fails")(expectation, a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
          testDied("3xA fails")(expectation, a *> a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
          testDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
          testDied("5xA fails")(
            expectation,
            a *> a *> a *> a *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          )
        )
      }, {
        val expectation = A.optional andThen A

        suite("optional is greedy")(
          suite("A.optional andThen A")(
            testDied("0xA fails")(expectation, ZIO.unit, hasUnsatisfiedExpectations),
            testDied("1xA fails")(expectation, a, hasUnsatisfiedExpectations),
            testValue("2xA passes")(expectation, a *> a, equalTo("A")),
            testDied("3xA fails")(expectation, a *> a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
            testDied("4xA fails")(expectation, a *> a *> a *> a, hasUnexpectedCall(PureModuleMock.SingleParam, 1)),
            testDied("5xA fails")(
              expectation,
              a *> a *> a *> a *> a,
              hasUnexpectedCall(PureModuleMock.SingleParam, 1)
            )
          )
        )
      }, {
        val expectation = ((A or B) andThen C) repeats (1 to 2)

        suite("((A or B) andThen C) repeats (1 to 2)")(
          testValue("A->C passes")(expectation, a *> c, equalTo("C")),
          testValue("B->C passes")(expectation, b *> c, equalTo("C")),
          testDied("A->C->A fails")(expectation, a *> c *> a, hasUnsatisfiedExpectations),
          testDied("A->C->B fails")(expectation, a *> c *> a, hasUnsatisfiedExpectations),
          testDied("B->C->A fails")(expectation, b *> c *> a, hasUnsatisfiedExpectations),
          testDied("B->C->B fails")(expectation, b *> c *> a, hasUnsatisfiedExpectations),
          testValue("A->C->A->C passes")(expectation, a *> c *> a *> c, equalTo("C")),
          testValue("A->C->B->C passes")(expectation, a *> c *> b *> c, equalTo("C")),
          testValue("B->C->A->C passes")(expectation, b *> c *> a *> c, equalTo("C")),
          testValue("B->C->B->C passes")(expectation, b *> c *> b *> c, equalTo("C")),
          testDied("A->C->A->C->A fails")(
            expectation,
            a *> c *> a *> c *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          ),
          testDied("A->C->B->C->A fails")(
            expectation,
            a *> c *> b *> c *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          ),
          testDied("B->C->A->C->A fails")(
            expectation,
            b *> c *> a *> c *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          ),
          testDied("B->C->B->C->A fails")(
            expectation,
            b *> c *> b *> c *> a,
            hasUnexpectedCall(PureModuleMock.SingleParam, 1)
          )
        )
      }
    )
  )
}

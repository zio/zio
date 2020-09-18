package zio.test.mock

import zio.test.mock.internal.{ ExpectationState, InvalidCall, MockException }
import zio.test.mock.module.{ ImpureModule, ImpureModuleMock }
import zio.test.{ suite, Assertion, Spec, TestFailure, TestSuccess, ZIOBaseSpec }
import zio.{ IO, UIO }

object BasicMethodMockSpec extends ZIOBaseSpec with MockSpecUtils[ImpureModule] {

  import Assertion._
  import Expectation._
  import ExpectationState._
  import InvalidCall._
  import MockException._

  def spec: Spec[Any, TestFailure[Any], TestSuccess] =
    suite("BasicMethodMockSpec")(
      suite("methods")(
        suite("zeroParams")(
          testValue("returns value")(
            ImpureModuleMock.ZeroParams(value("foo")),
            ImpureModule.zeroParams,
            equalTo("foo")
          ),
          testDied("returns failure")(
            ImpureModuleMock.ZeroParams(failure(new Exception("foo"))),
            ImpureModule.zeroParams,
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          )
        ),
        suite("zeroParamsWithParens")(
          testValue("returns value")(
            ImpureModuleMock.ZeroParamsWithParens(value("foo")),
            ImpureModule.zeroParamsWithParens(),
            equalTo("foo")
          ),
          testDied("returns failure")(
            ImpureModuleMock.ZeroParamsWithParens(failure(new Exception("foo"))),
            ImpureModule.zeroParamsWithParens(),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          )
        ),
        suite("singleParam")(
          testValue("returns value")(
            ImpureModuleMock.SingleParam(equalTo(1), value("foo")),
            ImpureModule.singleParam(1),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.SingleParam(equalTo(1), valueF(i => s"foo $i")),
            ImpureModule.singleParam(1),
            equalTo("foo 1")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.SingleParam(equalTo(1), valueM(i => UIO.succeed(s"foo $i"))),
            ImpureModule.singleParam(1),
            equalTo("foo 1")
          ),
          testDied("returns failure")(
            ImpureModuleMock.SingleParam(equalTo(1), failure(new Exception("foo"))),
            ImpureModule.singleParam(1),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.SingleParam(equalTo(1), failureF(i => new Exception(s"foo $i"))),
            ImpureModule.singleParam(1),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
          ),
          testDied("returns failureM")(
            ImpureModuleMock.SingleParam(equalTo(1), failureM(i => IO.fail(new Exception(s"foo $i")))),
            ImpureModule.singleParam(1),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
          )
        ),
        suite("manyParams")(
          testValue("returns value")(
            ImpureModuleMock.ManyParams(equalTo((1, "2", 3L)), value("foo")),
            ImpureModule.manyParams(1, "2", 3L),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.ManyParams(equalTo((1, "2", 3L)), valueF(i => s"foo $i")),
            ImpureModule.manyParams(1, "2", 3L),
            equalTo("foo (1,2,3)")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.ManyParams(equalTo((1, "2", 3L)), valueM(i => UIO.succeed(s"foo $i"))),
            ImpureModule.manyParams(1, "2", 3L),
            equalTo("foo (1,2,3)")
          ),
          testDied("returns failure")(
            ImpureModuleMock.ManyParams(equalTo((1, "2", 3L)), failure(new Exception("foo"))),
            ImpureModule.manyParams(1, "2", 3L),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.ManyParams(equalTo((1, "2", 3L)), failureF(i => new Exception(s"foo $i"))),
            ImpureModule.manyParams(1, "2", 3L),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo (1,2,3)")))
          ),
          testDied("returns failureM")(
            ImpureModuleMock.ManyParams(equalTo((1, "2", 3L)), failureM(i => IO.fail(new Exception(s"foo $i")))),
            ImpureModule.manyParams(1, "2", 3L),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo (1,2,3)")))
          )
        ),
        suite("manyParamLists")(
          testValue("returns value")(
            ImpureModuleMock.ManyParamLists(equalTo((1, "2", 3L)), value("foo")),
            ImpureModule.manyParamLists(1)("2")(3L),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.ManyParamLists(equalTo((1, "2", 3L)), valueF(i => s"foo $i")),
            ImpureModule.manyParamLists(1)("2")(3L),
            equalTo("foo (1,2,3)")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.ManyParamLists(equalTo((1, "2", 3L)), valueM(i => UIO.succeed(s"foo $i"))),
            ImpureModule.manyParamLists(1)("2")(3L),
            equalTo("foo (1,2,3)")
          ),
          testDied("returns failure")(
            ImpureModuleMock.ManyParamLists(equalTo((1, "2", 3L)), failure(new Exception("foo"))),
            ImpureModule.manyParamLists(1)("2")(3L),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.ManyParamLists(equalTo((1, "2", 3L)), failureF(i => new Exception(s"foo $i"))),
            ImpureModule.manyParamLists(1)("2")(3L),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo (1,2,3)")))
          ),
          testDied("returns failureM")(
            ImpureModuleMock.ManyParamLists(equalTo((1, "2", 3L)), failureM(i => IO.fail(new Exception(s"foo $i")))),
            ImpureModule.manyParamLists(1)("2")(3L),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo (1,2,3)")))
          )
        ),
        suite("command")(
          testValue("returns unit")(
            ImpureModuleMock.Command(),
            ImpureModule.command,
            isUnit
          )
        ),
        suite("parameterizedCommand")(
          testValue("returns unit")(
            ImpureModuleMock.ParameterizedCommand(equalTo(1)),
            ImpureModule.parameterizedCommand(1),
            isUnit
          )
        ),
        suite("overloaded")(
          suite("_0")(
            testValue("returns value")(
              ImpureModuleMock.Overloaded._0(equalTo(1), value("foo")),
              ImpureModule.overloaded(1),
              equalTo("foo")
            ),
            testValue("returns valueF")(
              ImpureModuleMock.Overloaded._0(equalTo(1), valueF(i => s"foo $i")),
              ImpureModule.overloaded(1),
              equalTo("foo 1")
            ),
            testValue("returns valueM")(
              ImpureModuleMock.Overloaded._0(equalTo(1), valueM(i => UIO.succeed(s"foo $i"))),
              ImpureModule.overloaded(1),
              equalTo("foo 1")
            ),
            testDied("returns failure")(
              ImpureModuleMock.Overloaded._0(equalTo(1), failure(new Exception("foo"))),
              ImpureModule.overloaded(1),
              isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
            ),
            testDied("returns failureF")(
              ImpureModuleMock.Overloaded._0(equalTo(1), failureF(i => new Exception(s"foo $i"))),
              ImpureModule.overloaded(1),
              isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
            ),
            testDied("returns failureM")(
              ImpureModuleMock.Overloaded._0(equalTo(1), failureM(i => IO.fail(new Exception(s"foo $i")))),
              ImpureModule.overloaded(1),
              isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
            )
          ),
          suite("_1")(
            testValue("returns value")(
              ImpureModuleMock.Overloaded._1(equalTo(1L), value("foo")),
              ImpureModule.overloaded(1L),
              equalTo("foo")
            ),
            testValue("returns valueF")(
              ImpureModuleMock.Overloaded._1(equalTo(1L), valueF(i => s"foo $i")),
              ImpureModule.overloaded(1L),
              equalTo("foo 1")
            ),
            testValue("returns valueM")(
              ImpureModuleMock.Overloaded._1(equalTo(1L), valueM(i => UIO.succeed(s"foo $i"))),
              ImpureModule.overloaded(1L),
              equalTo("foo 1")
            ),
            testDied("returns failure")(
              ImpureModuleMock.Overloaded._1(equalTo(1L), failure(new Exception("foo"))),
              ImpureModule.overloaded(1L),
              isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
            ),
            testDied("returns failureF")(
              ImpureModuleMock.Overloaded._1(equalTo(1L), failureF(i => new Exception(s"foo $i"))),
              ImpureModule.overloaded(1L),
              isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
            ),
            testDied("returns failureM")(
              ImpureModuleMock.Overloaded._1(equalTo(1L), failureM(i => IO.fail(new Exception(s"foo $i")))),
              ImpureModule.overloaded(1L),
              isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
            )
          )
        ),
        suite("varargs")(
          testValue("returns value")(
            ImpureModuleMock.Varargs(equalTo((1, Seq("2", "3"))), value("foo")),
            ImpureModule.varargs(1, "2", "3"),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.Varargs(
              equalTo((1, Seq("2", "3"))),
              valueF { case (a, b) =>
                s"foo $a, [${b.mkString(", ")}]"
              }
            ),
            ImpureModule.varargs(1, "2", "3"),
            equalTo("foo 1, [2, 3]")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.Varargs(
              equalTo((1, Seq("2", "3"))),
              valueM { case (a, b) =>
                UIO.succeed(s"foo $a, [${b.mkString(", ")}]")
              }
            ),
            ImpureModule.varargs(1, "2", "3"),
            equalTo("foo 1, [2, 3]")
          ),
          testDied("returns failure")(
            ImpureModuleMock.Varargs(equalTo((1, Seq("2", "3"))), failure(new Exception("foo"))),
            ImpureModule.varargs(1, "2", "3"),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.Varargs(
              equalTo((1, Seq("2", "3"))),
              failureF { case (a, b) =>
                new Exception(s"foo $a, [${b.mkString(", ")}]")
              }
            ),
            ImpureModule.varargs(1, "2", "3"),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1, [2, 3]")))
          ),
          testDied("returns failureM")(
            ImpureModuleMock.Varargs(
              equalTo((1, Seq("2", "3"))),
              failureM { case (a, b) =>
                IO.fail(new Exception(s"foo $a, [${b.mkString(", ")}]"))
              }
            ),
            ImpureModule.varargs(1, "2", "3"),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1, [2, 3]")))
          )
        ),
        suite("curriedVarargs")(
          testValue("returns value")(
            ImpureModuleMock.CurriedVarargs(equalTo((1, Seq("2", "3"), 4L, Seq('5', '6'))), value("foo")),
            ImpureModule.curriedVarargs(1, "2", "3")(4L, '5', '6'),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.CurriedVarargs(
              equalTo((1, Seq("2", "3"), 4L, Seq('5', '6'))),
              valueF { case (a, b, c, d) =>
                s"foo $a, [${b.mkString(", ")}], $c, [${d.mkString(", ")}]"
              }
            ),
            ImpureModule.curriedVarargs(1, "2", "3")(4L, '5', '6'),
            equalTo("foo 1, [2, 3], 4, [5, 6]")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.CurriedVarargs(
              equalTo((1, Seq("2", "3"), 4L, Seq('5', '6'))),
              valueM { case (a, b, c, d) =>
                UIO.succeed(s"foo $a, [${b.mkString(", ")}], $c, [${d.mkString(", ")}]")
              }
            ),
            ImpureModule.curriedVarargs(1, "2", "3")(4L, '5', '6'),
            equalTo("foo 1, [2, 3], 4, [5, 6]")
          ),
          testDied("returns failure")(
            ImpureModuleMock
              .CurriedVarargs(equalTo((1, Seq("2", "3"), 4L, Seq('5', '6'))), failure(new Exception("foo"))),
            ImpureModule.curriedVarargs(1, "2", "3")(4L, '5', '6'),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.CurriedVarargs(
              equalTo((1, Seq("2", "3"), 4L, Seq('5', '6'))),
              failureF { case (a, b, c, d) =>
                new Exception(s"foo $a, [${b.mkString(", ")}], $c, [${d.mkString(", ")}]")
              }
            ),
            ImpureModule.curriedVarargs(1, "2", "3")(4L, '5', '6'),
            isSubtype[Exception](
              hasField("message", _.getMessage, equalTo("foo 1, [2, 3], 4, [5, 6]"))
            )
          ),
          testDied("returns failureM")(
            ImpureModuleMock.CurriedVarargs(
              equalTo((1, Seq("2", "3"), 4L, Seq('5', '6'))),
              failureM { case (a, b, c, d) =>
                IO.fail(new Exception(s"foo $a, [${b.mkString(", ")}], $c, [${d.mkString(", ")}]"))
              }
            ),
            ImpureModule.curriedVarargs(1, "2", "3")(4L, '5', '6'),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1, [2, 3], 4, [5, 6]")))
          )
        ),
        suite("byName")(
          testValue("returns value")(
            ImpureModuleMock.ByName(equalTo(1), value("foo")),
            ImpureModule.byName(1),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.ByName(equalTo(1), valueF(i => s"foo $i")),
            ImpureModule.byName(1),
            equalTo("foo 1")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.ByName(equalTo(1), valueM(i => UIO.succeed(s"foo $i"))),
            ImpureModule.byName(1),
            equalTo("foo 1")
          ),
          testDied("returns failure")(
            ImpureModuleMock.ByName(equalTo(1), failure(new Exception("foo"))),
            ImpureModule.byName(1),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.ByName(equalTo(1), failureF(i => new Exception(s"foo $i"))),
            ImpureModule.byName(1),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
          ),
          testDied("returns failureM")(
            ImpureModuleMock.ByName(equalTo(1), failureM(i => IO.fail(new Exception(s"foo $i")))),
            ImpureModule.byName(1),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo 1")))
          )
        ),
        suite("maxParams")(
          testValue("returns value")(
            ImpureModuleMock.MaxParams(equalTo(intTuple22), value("foo")),
            (ImpureModule.maxParams _).tupled(intTuple22),
            equalTo("foo")
          ),
          testValue("returns valueF")(
            ImpureModuleMock.MaxParams(equalTo(intTuple22), valueF(i => s"foo $i")),
            (ImpureModule.maxParams _).tupled(intTuple22),
            equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
          ),
          testValue("returns valueM")(
            ImpureModuleMock.MaxParams(equalTo(intTuple22), valueM(i => UIO.succeed(s"foo $i"))),
            (ImpureModule.maxParams _).tupled(intTuple22),
            equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
          ),
          testDied("returns failure")(
            ImpureModuleMock.MaxParams(equalTo(intTuple22), failure(new Exception("foo"))),
            (ImpureModule.maxParams _).tupled(intTuple22),
            isSubtype[Exception](hasField("message", _.getMessage, equalTo("foo")))
          ),
          testDied("returns failureF")(
            ImpureModuleMock.MaxParams(equalTo(intTuple22), failureF(i => new Exception(s"foo $i"))),
            (ImpureModule.maxParams _).tupled(intTuple22),
            isSubtype[Exception](
              hasField(
                "message",
                _.getMessage,
                equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
              )
            )
          ),
          testDied("returns failureM")(
            ImpureModuleMock.MaxParams(equalTo(intTuple22), failureM(i => IO.fail(new Exception(s"foo $i")))),
            (ImpureModule.maxParams _).tupled(intTuple22),
            isSubtype[Exception](
              hasField(
                "message",
                _.getMessage,
                equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
              )
            )
          )
        )
      ),
      suite("assertions composition")(
        testValue("&&")(
          ImpureModuleMock.SingleParam(equalTo(3) && isWithin(1, 5), valueF(input => s"foo $input")),
          ImpureModule.singleParam(3),
          equalTo("foo 3")
        ),
        testValue("||")(
          ImpureModuleMock.SingleParam(equalTo(10) || isWithin(1, 5), valueF(input => s"foo $input")),
          ImpureModule.singleParam(3),
          equalTo("foo 3")
        )
      ),
      suite("expectations failure")(
        testDied("invalid arguments")(
          ImpureModuleMock.ParameterizedCommand(equalTo(1)),
          ImpureModule.parameterizedCommand(2),
          equalTo(InvalidCallException(List(InvalidArguments(ImpureModuleMock.ParameterizedCommand, 2, equalTo(1)))))
        ),
        testDied("invalid method")(
          ImpureModuleMock.ParameterizedCommand(equalTo(1)),
          ImpureModule.singleParam(1),
          equalTo(
            InvalidCallException(
              List(InvalidCapability(ImpureModuleMock.SingleParam, ImpureModuleMock.ParameterizedCommand, equalTo(1)))
            )
          )
        ), {
          type E0 = Chain[ImpureModule]
          type E1 = Call[ImpureModule, Int, Throwable, Unit]
          type L  = List[Expectation[ImpureModule]]
          type X  = UnsatisfiedExpectationsException[ImpureModule]

          def cmd(n: Int) = ImpureModuleMock.ParameterizedCommand(equalTo(n))

          def hasCall(index: Int, state: ExpectationState, invocations: List[Int]) =
            hasAt(index)(
              isSubtype[E1](
                hasField[E1, ExpectationState]("state", _.state, equalTo(state)) &&
                  hasField[E1, List[Int]]("invocations", _.invocations, equalTo(invocations))
              )
            )

          testDied("unsatisfied expectations")(
            cmd(1) ++ cmd(2) ++ cmd(3),
            ImpureModule.parameterizedCommand(1),
            isSubtype[X](
              hasField(
                "expectation",
                _.expectation,
                isSubtype[E0](
                  hasField[E0, L](
                    "children",
                    _.children,
                    isSubtype[L](
                      hasCall(0, Saturated, List(1)) &&
                        hasCall(1, Unsatisfied, List.empty) &&
                        hasCall(2, Unsatisfied, List.empty)
                    )
                  ) &&
                    hasField[E0, ExpectationState]("state", _.state, equalTo(PartiallySatisfied)) &&
                    hasField[E0, List[Int]]("invocations", _.invocations, equalTo(List(1)))
                )
              )
            )
          )
        }, {
          type M = Capability[ImpureModule, (Int, String, Long), Throwable, String]
          type X = UnexpectedCallException[ImpureModule, (Int, String, Long), Throwable, String]

          testDied("unexpected call")(
            ImpureModuleMock.SingleParam(equalTo(1), value("foo")),
            ImpureModule.singleParam(1) *> ImpureModule.manyParams(2, "3", 4L),
            isSubtype[X](
              hasField[X, M]("capability", _.capability, equalTo(ImpureModuleMock.ManyParams)) &&
                hasField[X, Any]("args", _.args, equalTo((2, "3", 4L)))
            )
          )
        }
      )
    )
}

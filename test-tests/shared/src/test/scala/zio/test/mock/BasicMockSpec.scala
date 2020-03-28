package zio.test.mock

import zio.duration._
import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.test.mock.module.{ Module, ModuleMock }
import zio.test.{ suite, Assertion, ZIOBaseSpec }
import zio.{ IO, UIO }

object BasicMockSpec extends ZIOBaseSpec with MockSpecUtils {

  import Assertion._
  import Expectation._
  import InvalidCall._
  import MockException._

  def spec = suite("BasicMockSpec")(
    suite("methods")(
      suite("static")(
        testSpec("returns value")(
          ModuleMock.Static(value("foo")),
          Module.static,
          equalTo("foo")
        ),
        testSpec("returns failure")(
          ModuleMock.Static(failure("foo")),
          Module.static.flip,
          equalTo("foo")
        )
      ),
      suite("zeroParams")(
        testSpec("returns value")(
          ModuleMock.ZeroParams(value("foo")),
          Module.zeroParams,
          equalTo("foo")
        ),
        testSpec("returns failure")(
          ModuleMock.ZeroParams(failure("foo")),
          Module.zeroParams.flip,
          equalTo("foo")
        )
      ),
      suite("zeroParamsWithParens")(
        testSpec("returns value")(
          ModuleMock.ZeroParamsWithParens(value("foo")),
          Module.zeroParamsWithParens(),
          equalTo("foo")
        ),
        testSpec("returns failure")(
          ModuleMock.ZeroParamsWithParens(failure("foo")),
          Module.zeroParamsWithParens().flip,
          equalTo("foo")
        )
      ),
      suite("singleParam")(
        testSpec("returns value")(
          ModuleMock.SingleParam(equalTo(1), value("foo")),
          Module.singleParam(1),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          ModuleMock.SingleParam(equalTo(1), valueF(i => s"foo $i")),
          Module.singleParam(1),
          equalTo("foo 1")
        ),
        testSpec("returns valueM")(
          ModuleMock.SingleParam(equalTo(1), valueM(i => UIO.succeedNow(s"foo $i"))),
          Module.singleParam(1),
          equalTo("foo 1")
        ),
        testSpec("returns failure")(
          ModuleMock.SingleParam(equalTo(1), failure("foo")),
          Module.singleParam(1).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          ModuleMock.SingleParam(equalTo(1), failureF(i => s"foo $i")),
          Module.singleParam(1).flip,
          equalTo("foo 1")
        ),
        testSpec("returns failureM")(
          ModuleMock.SingleParam(equalTo(1), failureM(i => IO.fail(s"foo $i"))),
          Module.singleParam(1).flip,
          equalTo("foo 1")
        )
      ),
      suite("manyParams")(
        testSpec("returns value")(
          ModuleMock.ManyParams(equalTo((1, "2", 3L)), value("foo")),
          Module.manyParams(1, "2", 3L),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          ModuleMock.ManyParams(equalTo((1, "2", 3L)), valueF(i => s"foo $i")),
          Module.manyParams(1, "2", 3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns valueM")(
          ModuleMock.ManyParams(equalTo((1, "2", 3L)), valueM(i => UIO.succeedNow(s"foo $i"))),
          Module.manyParams(1, "2", 3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failure")(
          ModuleMock.ManyParams(equalTo((1, "2", 3L)), failure("foo")),
          Module.manyParams(1, "2", 3L).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          ModuleMock.ManyParams(equalTo((1, "2", 3L)), failureF(i => s"foo $i")),
          Module.manyParams(1, "2", 3L).flip,
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failureM")(
          ModuleMock.ManyParams(equalTo((1, "2", 3L)), failureM(i => IO.fail(s"foo $i"))),
          Module.manyParams(1, "2", 3L).flip,
          equalTo("foo (1,2,3)")
        )
      ),
      suite("manyParamLists")(
        testSpec("returns value")(
          ModuleMock.ManyParamLists(equalTo((1, "2", 3L)), value("foo")),
          Module.manyParamLists(1)("2")(3L),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          ModuleMock.ManyParamLists(equalTo((1, "2", 3L)), valueF(i => s"foo $i")),
          Module.manyParamLists(1)("2")(3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns valueM")(
          ModuleMock.ManyParamLists(equalTo((1, "2", 3L)), valueM(i => UIO.succeedNow(s"foo $i"))),
          Module.manyParamLists(1)("2")(3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failure")(
          ModuleMock.ManyParamLists(equalTo((1, "2", 3L)), failure("foo")),
          Module.manyParamLists(1)("2")(3L).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          ModuleMock.ManyParamLists(equalTo((1, "2", 3L)), failureF(i => s"foo $i")),
          Module.manyParamLists(1)("2")(3L).flip,
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failureM")(
          ModuleMock.ManyParamLists(equalTo((1, "2", 3L)), failureM(i => IO.fail(s"foo $i"))),
          Module.manyParamLists(1)("2")(3L).flip,
          equalTo("foo (1,2,3)")
        )
      ),
      suite("command")(
        testSpec("returns unit")(
          ModuleMock.Command(),
          Module.command,
          isUnit
        )
      ),
      suite("parameterizedCommand")(
        testSpec("returns unit")(
          ModuleMock.ParameterizedCommand(equalTo(1)),
          Module.parameterizedCommand(1),
          isUnit
        )
      ),
      suite("overloaded")(
        suite("_0")(
          testSpec("returns value")(
            ModuleMock.Overloaded._0(equalTo(1), value("foo")),
            Module.overloaded(1),
            equalTo("foo")
          ),
          testSpec("returns valueF")(
            ModuleMock.Overloaded._0(equalTo(1), valueF(i => s"foo $i")),
            Module.overloaded(1),
            equalTo("foo 1")
          ),
          testSpec("returns valueM")(
            ModuleMock.Overloaded._0(equalTo(1), valueM(i => UIO.succeedNow(s"foo $i"))),
            Module.overloaded(1),
            equalTo("foo 1")
          ),
          testSpec("returns failure")(
            ModuleMock.Overloaded._0(equalTo(1), failure("foo")),
            Module.overloaded(1).flip,
            equalTo("foo")
          ),
          testSpec("returns failureF")(
            ModuleMock.Overloaded._0(equalTo(1), failureF(i => s"foo $i")),
            Module.overloaded(1).flip,
            equalTo("foo 1")
          ),
          testSpec("returns failureM")(
            ModuleMock.Overloaded._0(equalTo(1), failureM(i => IO.fail(s"foo $i"))),
            Module.overloaded(1).flip,
            equalTo("foo 1")
          )
        ),
        suite("_1")(
          testSpec("returns value")(
            ModuleMock.Overloaded._1(equalTo(1L), value("foo")),
            Module.overloaded(1L),
            equalTo("foo")
          ),
          testSpec("returns valueF")(
            ModuleMock.Overloaded._1(equalTo(1L), valueF(i => s"foo $i")),
            Module.overloaded(1L),
            equalTo("foo 1")
          ),
          testSpec("returns valueM")(
            ModuleMock.Overloaded._1(equalTo(1L), valueM(i => UIO.succeedNow(s"foo $i"))),
            Module.overloaded(1L),
            equalTo("foo 1")
          ),
          testSpec("returns failure")(
            ModuleMock.Overloaded._1(equalTo(1L), failure("foo")),
            Module.overloaded(1L).flip,
            equalTo("foo")
          ),
          testSpec("returns failureF")(
            ModuleMock.Overloaded._1(equalTo(1L), failureF(i => s"foo $i")),
            Module.overloaded(1L).flip,
            equalTo("foo 1")
          ),
          testSpec("returns failureM")(
            ModuleMock.Overloaded._1(equalTo(1L), failureM(i => IO.fail(s"foo $i"))),
            Module.overloaded(1L).flip,
            equalTo("foo 1")
          )
        )
      ),
      suite("maxParams")(
        testSpec("returns value")(
          ModuleMock.MaxParams(equalTo(intTuple22), value("foo")),
          (Module.maxParams _).tupled(intTuple22),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          ModuleMock.MaxParams(equalTo(intTuple22), valueF(i => s"foo $i")),
          (Module.maxParams _).tupled(intTuple22),
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        ),
        testSpec("returns valueM")(
          ModuleMock.MaxParams(equalTo(intTuple22), valueM(i => UIO.succeedNow(s"foo $i"))),
          (Module.maxParams _).tupled(intTuple22),
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        ),
        testSpec("returns failure")(
          ModuleMock.MaxParams(equalTo(intTuple22), failure("foo")),
          (Module.maxParams _).tupled(intTuple22).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          ModuleMock.MaxParams(equalTo(intTuple22), failureF(i => s"foo $i")),
          (Module.maxParams _).tupled(intTuple22).flip,
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        ),
        testSpec("returns failureM")(
          ModuleMock.MaxParams(equalTo(intTuple22), failureM(i => IO.fail(s"foo $i"))),
          (Module.maxParams _).tupled(intTuple22).flip,
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        )
      ),
      suite("looped")(
        testSpecTimeboxed("returns never")(500.millis)(
          ModuleMock.Looped(equalTo(1), never),
          Module.looped(1),
          isNone
        )
      )
    ),
    suite("assertions composition")(
      testSpec("&&")(
        ModuleMock.SingleParam(equalTo(3) && isWithin(1, 5), valueF(input => s"foo $input")),
        Module.singleParam(3),
        equalTo("foo 3")
      ),
      testSpec("||")(
        ModuleMock.SingleParam(equalTo(10) || isWithin(1, 5), valueF(input => s"foo $input")),
        Module.singleParam(3),
        equalTo("foo 3")
      )
    ),
    suite("expectations failure")(
      testSpecDied("invalid arguments")(
        ModuleMock.ParameterizedCommand(equalTo(1)),
        Module.parameterizedCommand(2),
        equalTo(InvalidCallException(List(InvalidArguments(ModuleMock.ParameterizedCommand, 2, equalTo(1)))))
      ),
      testSpecDied("invalid method")(
        ModuleMock.ParameterizedCommand(equalTo(1)),
        Module.singleParam(1),
        equalTo(
          InvalidCallException(List(InvalidMethod(ModuleMock.SingleParam, ModuleMock.ParameterizedCommand, equalTo(1))))
        )
      ), {
        type E0 = Chain[Module]
        type E1 = Call[Module, Int, Unit, Unit]
        type L  = List[Expectation[Module]]
        type X  = UnsatisfiedExpectationsException[Module]

        def cmd(n: Int) = ModuleMock.ParameterizedCommand(equalTo(n))

        def hasCall(index: Int, satisfied: Boolean, saturated: Boolean, invocations: List[Int]) =
          hasAt(index)(
            isSubtype[E1](
              hasField[E1, Boolean]("satisfied", _.satisfied, equalTo(satisfied)) &&
                hasField[E1, Boolean]("saturated", _.saturated, equalTo(saturated)) &&
                hasField[E1, List[Int]]("invocations", _.invocations, equalTo(invocations))
            )
          )

        testSpecDied("unsatisfied expectations")(
          cmd(1) ++ cmd(2) ++ cmd(3),
          Module.parameterizedCommand(1),
          isSubtype[X](
            hasField(
              "expectation",
              _.expectation,
              isSubtype[E0](
                hasField[E0, L](
                  "children",
                  _.children,
                  isSubtype[L](
                    hasCall(0, true, true, List(1)) &&
                      hasCall(1, false, false, List.empty) &&
                      hasCall(2, false, false, List.empty)
                  )
                ) &&
                  hasField[E0, Boolean]("satisfied", _.satisfied, equalTo(false)) &&
                  hasField[E0, Boolean]("saturated", _.saturated, equalTo(false)) &&
                  hasField[E0, List[Int]]("invocations", _.invocations, equalTo(List(1)))
              )
            )
          )
        )
      }, {
        type M = Method[Module, (Int, String, Long), String, String]
        type X = UnexpectedCallExpection[Module, (Int, String, Long), String, String]

        testSpecDied("unexpected call")(
          ModuleMock.SingleParam(equalTo(1), value("foo")),
          Module.singleParam(1) *> Module.manyParams(2, "3", 4L),
          isSubtype[X](
            hasField[X, M]("method", _.method, equalTo(ModuleMock.ManyParams)) &&
              hasField[X, Any]("args", _.args, equalTo((2, "3", 4L)))
          )
        )
      }
    )
  )
}

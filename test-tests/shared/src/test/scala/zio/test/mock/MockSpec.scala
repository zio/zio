package zio.test.mock

import zio.duration._
import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.test.{ suite, Assertion, ZIOBaseSpec }
import zio.{ IO, UIO }

object MockSpec extends ZIOBaseSpec {

  import Assertion._
  import Expectation._
  import InvalidCall._
  import MockException._
  import Module.Command._

  def spec = suite("MockSpec")(
    suite("methods")(
      suite("static")(
        testSpec("returns value")(
          Static returns value("foo"),
          Module.static,
          equalTo("foo")
        ),
        testSpec("returns failure")(
          Static returns failure("foo"),
          Module.static.flip,
          equalTo("foo")
        )
      ),
      suite("zeroParams")(
        testSpec("returns value")(
          ZeroParams returns value("foo"),
          Module.zeroParams,
          equalTo("foo")
        ),
        testSpec("returns failure")(
          ZeroParams returns failure("foo"),
          Module.zeroParams.flip,
          equalTo("foo")
        )
      ),
      suite("zeroParamsWithParens")(
        testSpec("returns value")(
          ZeroParamsWithParens returns value("foo"),
          Module.zeroParamsWithParens(),
          equalTo("foo")
        ),
        testSpec("returns failure")(
          ZeroParamsWithParens returns failure("foo"),
          Module.zeroParamsWithParens().flip,
          equalTo("foo")
        )
      ),
      suite("singleParam")(
        testSpec("returns value")(
          SingleParam(equalTo(1)) returns value("foo"),
          Module.singleParam(1),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          SingleParam(equalTo(1)) returns valueF(i => s"foo $i"),
          Module.singleParam(1),
          equalTo("foo 1")
        ),
        testSpec("returns valueM")(
          SingleParam(equalTo(1)) returns valueM(i => UIO.succeed(s"foo $i")),
          Module.singleParam(1),
          equalTo("foo 1")
        ),
        testSpec("returns failure")(
          SingleParam(equalTo(1)) returns failure("foo"),
          Module.singleParam(1).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          SingleParam(equalTo(1)) returns failureF(i => s"foo $i"),
          Module.singleParam(1).flip,
          equalTo("foo 1")
        ),
        testSpec("returns failureM")(
          SingleParam(equalTo(1)) returns failureM(i => IO.fail(s"foo $i")),
          Module.singleParam(1).flip,
          equalTo("foo 1")
        )
      ),
      suite("manyParams")(
        testSpec("returns value")(
          ManyParams(equalTo((1, "2", 3L))) returns value("foo"),
          Module.manyParams(1, "2", 3L),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          ManyParams(equalTo((1, "2", 3L))) returns valueF(i => s"foo $i"),
          Module.manyParams(1, "2", 3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns valueM")(
          ManyParams(equalTo((1, "2", 3L))) returns valueM(i => UIO.succeed(s"foo $i")),
          Module.manyParams(1, "2", 3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failure")(
          ManyParams(equalTo((1, "2", 3L))) returns failure("foo"),
          Module.manyParams(1, "2", 3L).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          ManyParams(equalTo((1, "2", 3L))) returns failureF(i => s"foo $i"),
          Module.manyParams(1, "2", 3L).flip,
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failureM")(
          ManyParams(equalTo((1, "2", 3L))) returns failureM(i => IO.fail(s"foo $i")),
          Module.manyParams(1, "2", 3L).flip,
          equalTo("foo (1,2,3)")
        )
      ),
      suite("manyParamLists")(
        testSpec("returns value")(
          ManyParamLists(equalTo((1, "2", 3L))) returns value("foo"),
          Module.manyParamLists(1)("2")(3L),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          ManyParamLists(equalTo((1, "2", 3L))) returns valueF(i => s"foo $i"),
          Module.manyParamLists(1)("2")(3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns valueM")(
          ManyParamLists(equalTo((1, "2", 3L))) returns valueM(i => UIO.succeed(s"foo $i")),
          Module.manyParamLists(1)("2")(3L),
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failure")(
          ManyParamLists(equalTo((1, "2", 3L))) returns failure("foo"),
          Module.manyParamLists(1)("2")(3L).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          ManyParamLists(equalTo((1, "2", 3L))) returns failureF(i => s"foo $i"),
          Module.manyParamLists(1)("2")(3L).flip,
          equalTo("foo (1,2,3)")
        ),
        testSpec("returns failureM")(
          ManyParamLists(equalTo((1, "2", 3L))) returns failureM(i => IO.fail(s"foo $i")),
          Module.manyParamLists(1)("2")(3L).flip,
          equalTo("foo (1,2,3)")
        )
      ),
      suite("command")(
        testSpec("returns unit")(
          Command(equalTo(1)) returns unit,
          Module.command(1),
          isUnit
        )
      ),
      suite("overloaded")(
        suite("_0")(
          testSpec("returns value")(
            Overloaded._0(equalTo(1)) returns value("foo"),
            Module.overloaded(1),
            equalTo("foo")
          ),
          testSpec("returns valueF")(
            Overloaded._0(equalTo(1)) returns valueF(i => s"foo $i"),
            Module.overloaded(1),
            equalTo("foo 1")
          ),
          testSpec("returns valueM")(
            Overloaded._0(equalTo(1)) returns valueM(i => UIO.succeed(s"foo $i")),
            Module.overloaded(1),
            equalTo("foo 1")
          ),
          testSpec("returns failure")(
            Overloaded._0(equalTo(1)) returns failure("foo"),
            Module.overloaded(1).flip,
            equalTo("foo")
          ),
          testSpec("returns failureF")(
            Overloaded._0(equalTo(1)) returns failureF(i => s"foo $i"),
            Module.overloaded(1).flip,
            equalTo("foo 1")
          ),
          testSpec("returns failureM")(
            Overloaded._0(equalTo(1)) returns failureM(i => IO.fail(s"foo $i")),
            Module.overloaded(1).flip,
            equalTo("foo 1")
          )
        ),
        suite("_1")(
          testSpec("returns value")(
            Overloaded._1(equalTo(1L)) returns value("foo"),
            Module.overloaded(1L),
            equalTo("foo")
          ),
          testSpec("returns valueF")(
            Overloaded._1(equalTo(1L)) returns valueF(i => s"foo $i"),
            Module.overloaded(1L),
            equalTo("foo 1")
          ),
          testSpec("returns valueM")(
            Overloaded._1(equalTo(1L)) returns valueM(i => UIO.succeed(s"foo $i")),
            Module.overloaded(1L),
            equalTo("foo 1")
          ),
          testSpec("returns failure")(
            Overloaded._1(equalTo(1L)) returns failure("foo"),
            Module.overloaded(1L).flip,
            equalTo("foo")
          ),
          testSpec("returns failureF")(
            Overloaded._1(equalTo(1L)) returns failureF(i => s"foo $i"),
            Module.overloaded(1L).flip,
            equalTo("foo 1")
          ),
          testSpec("returns failureM")(
            Overloaded._1(equalTo(1L)) returns failureM(i => IO.fail(s"foo $i")),
            Module.overloaded(1L).flip,
            equalTo("foo 1")
          )
        )
      ),
      suite("maxParams")(
        testSpec("returns value")(
          MaxParams(equalTo(intTuple22)) returns value("foo"),
          (Module.maxParams _).tupled(intTuple22),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          MaxParams(equalTo(intTuple22)) returns valueF(i => s"foo $i"),
          (Module.maxParams _).tupled(intTuple22),
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        ),
        testSpec("returns valueM")(
          MaxParams(equalTo(intTuple22)) returns valueM(i => UIO.succeed(s"foo $i")),
          (Module.maxParams _).tupled(intTuple22),
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        ),
        testSpec("returns failure")(
          MaxParams(equalTo(intTuple22)) returns failure("foo"),
          (Module.maxParams _).tupled(intTuple22).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          MaxParams(equalTo(intTuple22)) returns failureF(i => s"foo $i"),
          (Module.maxParams _).tupled(intTuple22).flip,
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        ),
        testSpec("returns failureM")(
          MaxParams(equalTo(intTuple22)) returns failureM(i => IO.fail(s"foo $i")),
          (Module.maxParams _).tupled(intTuple22).flip,
          equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
        )
      ),
      suite("looped")(
        testSpecTimeboxed("returns never")(500.millis)(
          Looped(equalTo(1)) returns never,
          Module.looped(1),
          isNone
        )
      )
    ),
    suite("assertions composition")(
      testSpec("&&")(
        SingleParam(equalTo(3) && isWithin(1, 5)) returns valueF(input => s"foo $input"),
        Module.singleParam(3),
        equalTo("foo 3")
      ),
      testSpec("||")(
        SingleParam(equalTo(10) || isWithin(1, 5)) returns valueF(input => s"foo $input"),
        Module.singleParam(3),
        equalTo("foo 3")
      )
    ),
    suite("expectations failure")(
      testSpecDied("invalid arguments")(
        Command(equalTo(1)) returns unit,
        Module.command(2),
        equalTo(InvalidCallException(List(InvalidArguments(Command, 2, equalTo(1)))))
      ),
      testSpecDied("invalid method")(
        Command(equalTo(1)) returns unit,
        Module.singleParam(1),
        equalTo(InvalidCallException(List(InvalidMethod(SingleParam, Command, equalTo(1)))))
      ), {
        type E0 = Chain[Module]
        type E1 = Call[Module, Int, Unit, Unit]
        type L  = List[Expectation[Module]]
        type X  = UnsatisfiedExpectationsException[Module]

        def cmd(n: Int) = Command(equalTo(n)) returns unit

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
          Module.command(1),
          isSubtype[X](
            hasField(
              "expectation",
              _.expectation,
              isSubtype[E0](
                hasField[E0, L](
                  "children",
                  _.children,
                  isSubtype[L](
                    /**
                     * No idea why this takes so long... @adamgfraser could you take a look?
                     * It seems useing nested `isSubtype` and/or `hasField` explodes the runtime.
                     *
                     * 1 hasCall -> about 10s,
                     * 2 hasCall -> about 17s,
                     * 3 hasCall -> over 1 minute (times out)
                     */
                    //hasCall(0, true, true, List(1)) &&
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
        type M = Method[Module, (Int, String, Long), String]
        type X = UnexpectedCallExpection[Module, (Int, String, Long), String]

        testSpecDied("unexpected call")(
          SingleParam(equalTo(1)) returns value("foo"),
          Module.singleParam(1) *> Module.manyParams(2, "3", 4L),
          isSubtype[X](
            hasField[X, M]("method", _.method, equalTo(ManyParams)) &&
              hasField[X, Any]("args", _.args, equalTo((2, "3", 4L)))
          )
        )
      }
    )
  )
}

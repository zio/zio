package zio.test.mock

import ExpectationSpecUtils._

import zio.duration._
import zio.test.Assertion.{ equalTo, isNone, isUnit, isWithin }
import zio.test.mock.Expectation.{ failure, failureF, failureM, never, unit, value, valueF, valueM }
import zio.test.mock.MockException.{
  InvalidArgumentsException,
  InvalidMethodException,
  UnexpectedCallExpection,
  UnmetExpectationsException
}
import zio.test.{ suite, ZIOBaseSpec }
import zio.{ IO, UIO }

object ExpectationSpec extends ZIOBaseSpec {
  import Module.mockableModule

  def spec = suite("ExpectationSpec")(
    suite("static")(
      testSpec("returns value")(
        Module.static returns value("foo"),
        Module.>.static,
        equalTo("foo")
      ),
      testSpec("returns failure")(
        Module.static returns failure("foo"),
        Module.>.static.flip,
        equalTo("foo")
      )
    ),
    suite("zeroParams")(
      testSpec("returns value")(
        Module.zeroParams returns value("foo"),
        Module.>.zeroParams,
        equalTo("foo")
      ),
      testSpec("returns failure")(
        Module.zeroParams returns failure("foo"),
        Module.>.zeroParams.flip,
        equalTo("foo")
      )
    ),
    suite("zeroParamsWithParens")(
      testSpec("returns value")(
        Module.zeroParamsWithParens returns value("foo"),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      ),
      testSpec("returns failure")(
        Module.zeroParamsWithParens returns failure("foo"),
        Module.>.zeroParamsWithParens().flip,
        equalTo("foo")
      )
    ),
    suite("singleParam")(
      testSpec("returns value")(
        Module.singleParam(equalTo(1)) returns value("foo"),
        Module.>.singleParam(1),
        equalTo("foo")
      ),
      testSpec("returns valueF")(
        Module.singleParam(equalTo(1)) returns valueF(i => s"foo $i"),
        Module.>.singleParam(1),
        equalTo("foo 1")
      ),
      testSpec("returns valueM")(
        Module.singleParam(equalTo(1)) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.singleParam(1),
        equalTo("foo 1")
      ),
      testSpec("returns failure")(
        Module.singleParam(equalTo(1)) returns failure("foo"),
        Module.>.singleParam(1).flip,
        equalTo("foo")
      ),
      testSpec("returns failureF")(
        Module.singleParam(equalTo(1)) returns failureF(i => s"foo $i"),
        Module.>.singleParam(1).flip,
        equalTo("foo 1")
      ),
      testSpec("returns failureM")(
        Module.singleParam(equalTo(1)) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.singleParam(1).flip,
        equalTo("foo 1")
      )
    ),
    suite("manyParams")(
      testSpec("returns value")(
        Module.manyParams(equalTo((1, "2", 3L))) returns value("foo"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo")
      ),
      testSpec("returns valueF")(
        Module.manyParams(equalTo((1, "2", 3L))) returns valueF(i => s"foo $i"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      ),
      testSpec("returns valueM")(
        Module.manyParams(equalTo((1, "2", 3L))) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      ),
      testSpec("returns failure")(
        Module.manyParams(equalTo((1, "2", 3L))) returns failure("foo"),
        Module.>.manyParams(1, "2", 3L).flip,
        equalTo("foo")
      ),
      testSpec("returns failureF")(
        Module.manyParams(equalTo((1, "2", 3L))) returns failureF(i => s"foo $i"),
        Module.>.manyParams(1, "2", 3L).flip,
        equalTo("foo (1,2,3)")
      ),
      testSpec("returns failureM")(
        Module.manyParams(equalTo((1, "2", 3L))) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.manyParams(1, "2", 3L).flip,
        equalTo("foo (1,2,3)")
      )
    ),
    suite("manyParamLists")(
      testSpec("returns value")(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns value("foo"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo")
      ),
      testSpec("returns valueF")(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns valueF(i => s"foo $i"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      ),
      testSpec("returns valueM")(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      ),
      testSpec("returns failure")(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns failure("foo"),
        Module.>.manyParamLists(1)("2")(3L).flip,
        equalTo("foo")
      ),
      testSpec("returns failureF")(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns failureF(i => s"foo $i"),
        Module.>.manyParamLists(1)("2")(3L).flip,
        equalTo("foo (1,2,3)")
      ),
      testSpec("returns failureM")(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.manyParamLists(1)("2")(3L).flip,
        equalTo("foo (1,2,3)")
      )
    ),
    suite("command")(
      testSpec("returns unit")(
        Module.command(equalTo(1)) returns unit,
        Module.>.command(1),
        isUnit
      )
    ),
    suite("overloaded")(
      suite("_0")(
        testSpec("returns value")(
          Module.overloaded._0(equalTo(1)) returns value("foo"),
          Module.>.overloaded(1),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          Module.overloaded._0(equalTo(1)) returns valueF(i => s"foo $i"),
          Module.>.overloaded(1),
          equalTo("foo 1")
        ),
        testSpec("returns valueM")(
          Module.overloaded._0(equalTo(1)) returns valueM(i => UIO.succeed(s"foo $i")),
          Module.>.overloaded(1),
          equalTo("foo 1")
        ),
        testSpec("returns failure")(
          Module.overloaded._0(equalTo(1)) returns failure("foo"),
          Module.>.overloaded(1).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          Module.overloaded._0(equalTo(1)) returns failureF(i => s"foo $i"),
          Module.>.overloaded(1).flip,
          equalTo("foo 1")
        ),
        testSpec("returns failureM")(
          Module.overloaded._0(equalTo(1)) returns failureM(i => IO.fail(s"foo $i")),
          Module.>.overloaded(1).flip,
          equalTo("foo 1")
        )
      ),
      suite("_1")(
        testSpec("returns value")(
          Module.overloaded._1(equalTo(1L)) returns value("foo"),
          Module.>.overloaded(1L),
          equalTo("foo")
        ),
        testSpec("returns valueF")(
          Module.overloaded._1(equalTo(1L)) returns valueF(i => s"foo $i"),
          Module.>.overloaded(1L),
          equalTo("foo 1")
        ),
        testSpec("returns valueM")(
          Module.overloaded._1(equalTo(1L)) returns valueM(i => UIO.succeed(s"foo $i")),
          Module.>.overloaded(1L),
          equalTo("foo 1")
        ),
        testSpec("returns failure")(
          Module.overloaded._1(equalTo(1L)) returns failure("foo"),
          Module.>.overloaded(1L).flip,
          equalTo("foo")
        ),
        testSpec("returns failureF")(
          Module.overloaded._1(equalTo(1L)) returns failureF(i => s"foo $i"),
          Module.>.overloaded(1L).flip,
          equalTo("foo 1")
        ),
        testSpec("returns failureM")(
          Module.overloaded._1(equalTo(1L)) returns failureM(i => IO.fail(s"foo $i")),
          Module.>.overloaded(1L).flip,
          equalTo("foo 1")
        )
      )
    ),
    suite("maxParams")(
      testSpec("returns value")(
        Module.maxParams(equalTo(intTuple22)) returns value("foo"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo")
      ),
      testSpec("returns valueF")(
        Module.maxParams(equalTo(intTuple22)) returns valueF(i => s"foo $i"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      ),
      testSpec("returns valueM")(
        Module.maxParams(equalTo(intTuple22)) returns valueM(i => UIO.succeed(s"foo $i")),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      ),
      testSpec("returns failure")(
        Module.maxParams(equalTo(intTuple22)) returns failure("foo"),
        (Module.>.maxParams _).tupled(intTuple22).flip,
        equalTo("foo")
      ),
      testSpec("returns failureF")(
        Module.maxParams(equalTo(intTuple22)) returns failureF(i => s"foo $i"),
        (Module.>.maxParams _).tupled(intTuple22).flip,
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      ),
      testSpec("returns failureM")(
        Module.maxParams(equalTo(intTuple22)) returns failureM(i => IO.fail(s"foo $i")),
        (Module.>.maxParams _).tupled(intTuple22).flip,
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )
    ),
    suite("looped")(
      testSpecTimeboxed("returns never")(500.millis)(
        Module.looped(equalTo(1)) returns never,
        Module.>.looped(1),
        isNone
      )
    ),
    suite("assertions composition")(
      testSpec("&&")(
        Module.singleParam(equalTo(3) && isWithin(1, 5)) returns valueF(input => s"foo $input"),
        Module.>.singleParam(3),
        equalTo("foo 3")
      ),
      testSpec("||")(
        Module.singleParam(equalTo(10) || isWithin(1, 5)) returns valueF(input => s"foo $input"),
        Module.>.singleParam(3),
        equalTo("foo 3")
      )
    ),
    suite("expectations composition")(
      testSpec("*>")(
        (
          (Module.singleParam(equalTo(1)) returns value("foo")) *>
            (Module.static returns value("bar"))
        ),
        Module.>.singleParam(1) *> Module.>.static,
        equalTo("bar")
      )
    ),
    suite("expectations failure")(
      testSpecDied("invalid arguments")(
        Module.command(equalTo(1)) returns unit,
        Module.>.command(2),
        equalTo(InvalidArgumentsException(Module.command, 2, equalTo(1)))
      ),
      testSpecDied("invalid method")(
        Module.command(equalTo(1)) returns unit,
        Module.>.singleParam(1),
        equalTo(
          InvalidMethodException(Module.singleParam, Module.command, equalTo(1))
        )
      ),
      testSpecDied("unmet expectations")(
        (
          (Module.command(equalTo(1)) returns unit) *>
            (Module.command(equalTo(2)) returns unit) *>
            (Module.command(equalTo(3)) returns unit)
        ),
        Module.>.command(1),
        equalTo(
          UnmetExpectationsException(
            List(
              Module.command -> (equalTo(2)),
              Module.command -> (equalTo(3))
            )
          )
        )
      ),
      testSpecDied("unexpected call")(
        Module.singleParam(equalTo(1)) returns value("foo"),
        Module.>.singleParam(1) *> Module.>.manyParams(2, "3", 4L),
        equalTo(UnexpectedCallExpection(Module.manyParams, (2, "3", 4L)))
      )
    ), {
      val e1 = Module.command(equalTo(1)) returns unit
      val e2 = Module.singleParam(equalTo(2)) returns value("foo")
      val e3 = Module.command(equalTo(3)) returns unit
      val app: zio.ZIO[zio.Has[Module], Any, String] =
        for {
          _ <- Module.>.command(1)
          v <- Module.>.singleParam(2)
          _ <- Module.>.command(3)
        } yield v

      suite("expectation building")(
        testSpec("flatMap")(e1.flatMap(_ => e2).flatMap(_ => e3), app, equalTo("foo")),
        testSpec("zipRight")(e1.zipRight(e2).zipRight(e3), app, equalTo("foo")),
        testSpec("*>")(e1 *> e2 *> e3, app, equalTo("foo"))
      )
    }
  )
}

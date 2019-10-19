/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test.mock

import scala.concurrent.Future

import zio.{ IO, Managed, UIO, ZIO }
import zio.clock.Clock
import zio.duration._
import zio.test.{ assertM, testM, Assertion, Async, AsyncBaseSpec }
import zio.test.Assertion.{ equalTo, isNone, isUnit, isWithin }
import zio.test.TestUtils.{ label, succeeded }
import zio.test.mock.Expectation.{ failure, failureF, failureM, never, unit, value, valueF, valueM }
import zio.test.mock.ExpectationSpecUtils.{ intTuple22, Module }
import zio.test.mock.MockException.{ InvalidArgumentsException, InvalidMethodException, UnmetExpectationsException }

object ExpectationSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(static.returnsValue, "static returns value"),
    label(static.returnsFailure, "static returns failure"),
    label(zeroParams.returnsValue, "zeroParams returns value"),
    label(zeroParams.returnsFailure, "zeroParams returns failure"),
    label(zeroParamsWithParens.returnsValue, "zeroParamsWithParens returns value"),
    label(zeroParamsWithParens.returnsFailure, "zeroParamsWithParens returns failure"),
    label(singleParam.returnsValue, "singleParam returns value"),
    label(singleParam.returnsValueF, "singleParam returns valueF"),
    label(singleParam.returnsValueM, "singleParam returns valueM"),
    label(singleParam.returnsFailure, "singleParam returns failure"),
    label(singleParam.returnsFailureF, "singleParam returns failureF"),
    label(singleParam.returnsFailureM, "singleParam returns failureM"),
    label(manyParams.returnsValue, "manyParams returns value"),
    label(manyParams.returnsValueF, "manyParams returns valueF"),
    label(manyParams.returnsValueM, "manyParams returns valueM"),
    label(manyParams.returnsFailure, "manyParams returns failure"),
    label(manyParams.returnsFailureF, "manyParams returns failureF"),
    label(manyParams.returnsFailureM, "manyParams returns failureM"),
    label(manyParamLists.returnsValue, "manyParamLists returns value"),
    label(manyParamLists.returnsValueF, "manyParamLists returns valueF"),
    label(manyParamLists.returnsValueM, "manyParamLists returns valueM"),
    label(manyParamLists.returnsFailure, "manyParamLists returns failure"),
    label(manyParamLists.returnsFailureF, "manyParamLists returns failureF"),
    label(manyParamLists.returnsFailureM, "manyParamLists returns failureM"),
    label(command.returnsUnit, "command returns unit"),
    label(overloaded0.returnsValue, "overloaded0 returns value"),
    label(overloaded0.returnsValueF, "overloaded0 returns valueF"),
    label(overloaded0.returnsValueM, "overloaded0 returns valueM"),
    label(overloaded0.returnsFailure, "overloaded0 returns failure"),
    label(overloaded0.returnsFailureF, "overloaded0 returns failureF"),
    label(overloaded0.returnsFailureM, "overloaded0 returns failureM"),
    label(overloaded1.returnsValue, "overloaded1 returns value"),
    label(overloaded1.returnsValueF, "overloaded1 returns valueF"),
    label(overloaded1.returnsValueM, "overloaded1 returns valueM"),
    label(overloaded1.returnsFailure, "overloaded1 returns failure"),
    label(overloaded1.returnsFailureF, "overloaded1 returns failureF"),
    label(overloaded1.returnsFailureM, "overloaded1 returns failureM"),
    label(maxParams.returnsValue, "maxParams returns value"),
    label(maxParams.returnsValueF, "maxParams returns valueF"),
    label(maxParams.returnsValueM, "maxParams returns valueM"),
    label(maxParams.returnsFailure, "maxParams returns failure"),
    label(maxParams.returnsFailureF, "maxParams returns failureF"),
    label(maxParams.returnsFailureM, "maxParams returns failureM"),
    label(looped.returnsNever, "looped returns never"),
    label(assertionsComposition.&&, "assertions composition &&"),
    label(assertionsComposition.||, "assertions composition ||"),
    label(specComposition.*>, "spec composition *>"),
    label(specFailure.invalidArguments, "spec failure invalid arguments"),
    label(specFailure.invalidMethod, "spec failure invalid method"),
    label(specFailure.unmetExpectations, "spec failure unmet expectations")
  )

  object static {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.static returns value("foo"),
        Module.>.static,
        equalTo("foo")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.static returns failure("foo"),
        Module.>.static.flip,
        equalTo("foo")
      )
  }

  object zeroParams {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.zeroParams returns value("foo"),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.zeroParams returns failure("foo"),
        Module.>.zeroParams.flip,
        equalTo("foo")
      )
  }

  object zeroParamsWithParens {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.zeroParamsWithParens returns value("foo"),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.zeroParamsWithParens returns failure("foo"),
        Module.>.zeroParamsWithParens().flip,
        equalTo("foo")
      )
  }

  object singleParam {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(1)) returns value("foo"),
        Module.>.singleParam(1),
        equalTo("foo")
      )

    def returnsValueF: Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(1)) returns valueF(i => s"foo $i"),
        Module.>.singleParam(1),
        equalTo("foo 1")
      )

    def returnsValueM: Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(1)) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.singleParam(1),
        equalTo("foo 1")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(1)) returns failure("foo"),
        Module.>.singleParam(1).flip,
        equalTo("foo")
      )

    def returnsFailureF: Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(1)) returns failureF(i => s"foo $i"),
        Module.>.singleParam(1).flip,
        equalTo("foo 1")
      )

    def returnsFailureM: Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(1)) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.singleParam(1).flip,
        equalTo("foo 1")
      )
  }

  object manyParams {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.manyParams(equalTo((1, "2", 3L))) returns value("foo"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo")
      )

    def returnsValueF: Future[Boolean] =
      testSpec(
        Module.manyParams(equalTo((1, "2", 3L))) returns valueF(i => s"foo $i"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      )

    def returnsValueM: Future[Boolean] =
      testSpec(
        Module.manyParams(equalTo((1, "2", 3L))) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.manyParams(equalTo((1, "2", 3L))) returns failure("foo"),
        Module.>.manyParams(1, "2", 3L).flip,
        equalTo("foo")
      )

    def returnsFailureF: Future[Boolean] =
      testSpec(
        Module.manyParams(equalTo((1, "2", 3L))) returns failureF(i => s"foo $i"),
        Module.>.manyParams(1, "2", 3L).flip,
        equalTo("foo (1,2,3)")
      )

    def returnsFailureM: Future[Boolean] =
      testSpec(
        Module.manyParams(equalTo((1, "2", 3L))) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.manyParams(1, "2", 3L).flip,
        equalTo("foo (1,2,3)")
      )
  }

  object manyParamLists {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns value("foo"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo")
      )

    def returnsValueF: Future[Boolean] =
      testSpec(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns valueF(i => s"foo $i"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      )

    def returnsValueM: Future[Boolean] =
      testSpec(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns failure("foo"),
        Module.>.manyParamLists(1)("2")(3L).flip,
        equalTo("foo")
      )

    def returnsFailureF: Future[Boolean] =
      testSpec(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns failureF(i => s"foo $i"),
        Module.>.manyParamLists(1)("2")(3L).flip,
        equalTo("foo (1,2,3)")
      )

    def returnsFailureM: Future[Boolean] =
      testSpec(
        Module.manyParamLists(equalTo((1, "2", 3L))) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.manyParamLists(1)("2")(3L).flip,
        equalTo("foo (1,2,3)")
      )
  }

  object command {

    def returnsUnit: Future[Boolean] =
      testSpec(
        Module.command(equalTo(1)) returns unit,
        Module.>.command(1),
        isUnit
      )
  }

  object overloaded0 {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.overloaded._0(equalTo(1)) returns value("foo"),
        Module.>.overloaded(1),
        equalTo("foo")
      )

    def returnsValueF: Future[Boolean] =
      testSpec(
        Module.overloaded._0(equalTo(1)) returns valueF(i => s"foo $i"),
        Module.>.overloaded(1),
        equalTo("foo 1")
      )

    def returnsValueM: Future[Boolean] =
      testSpec(
        Module.overloaded._0(equalTo(1)) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.overloaded(1),
        equalTo("foo 1")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.overloaded._0(equalTo(1)) returns failure("foo"),
        Module.>.overloaded(1).flip,
        equalTo("foo")
      )

    def returnsFailureF: Future[Boolean] =
      testSpec(
        Module.overloaded._0(equalTo(1)) returns failureF(i => s"foo $i"),
        Module.>.overloaded(1).flip,
        equalTo("foo 1")
      )

    def returnsFailureM: Future[Boolean] =
      testSpec(
        Module.overloaded._0(equalTo(1)) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.overloaded(1).flip,
        equalTo("foo 1")
      )
  }

  object overloaded1 {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.overloaded._1(equalTo(1L)) returns value("foo"),
        Module.>.overloaded(1L),
        equalTo("foo")
      )

    def returnsValueF: Future[Boolean] =
      testSpec(
        Module.overloaded._1(equalTo(1L)) returns valueF(i => s"foo $i"),
        Module.>.overloaded(1L),
        equalTo("foo 1")
      )

    def returnsValueM: Future[Boolean] =
      testSpec(
        Module.overloaded._1(equalTo(1L)) returns valueM(i => UIO.succeed(s"foo $i")),
        Module.>.overloaded(1L),
        equalTo("foo 1")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.overloaded._1(equalTo(1L)) returns failure("foo"),
        Module.>.overloaded(1L).flip,
        equalTo("foo")
      )

    def returnsFailureF: Future[Boolean] =
      testSpec(
        Module.overloaded._1(equalTo(1L)) returns failureF(i => s"foo $i"),
        Module.>.overloaded(1L).flip,
        equalTo("foo 1")
      )

    def returnsFailureM: Future[Boolean] =
      testSpec(
        Module.overloaded._1(equalTo(1L)) returns failureM(i => IO.fail(s"foo $i")),
        Module.>.overloaded(1L).flip,
        equalTo("foo 1")
      )
  }

  object maxParams {

    def returnsValue: Future[Boolean] =
      testSpec(
        Module.maxParams(equalTo(intTuple22)) returns value("foo"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo")
      )

    def returnsValueF: Future[Boolean] =
      testSpec(
        Module.maxParams(equalTo(intTuple22)) returns valueF(i => s"foo $i"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )

    def returnsValueM: Future[Boolean] =
      testSpec(
        Module.maxParams(equalTo(intTuple22)) returns valueM(i => UIO.succeed(s"foo $i")),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )

    def returnsFailure: Future[Boolean] =
      testSpec(
        Module.maxParams(equalTo(intTuple22)) returns failure("foo"),
        (Module.>.maxParams _).tupled(intTuple22).flip,
        equalTo("foo")
      )

    def returnsFailureF: Future[Boolean] =
      testSpec(
        Module.maxParams(equalTo(intTuple22)) returns failureF(i => s"foo $i"),
        (Module.>.maxParams _).tupled(intTuple22).flip,
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )

    def returnsFailureM: Future[Boolean] =
      testSpec(
        Module.maxParams(equalTo(intTuple22)) returns failureM(i => IO.fail(s"foo $i")),
        (Module.>.maxParams _).tupled(intTuple22).flip,
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )
  }

  object looped {

    def returnsNever: Future[Boolean] =
      testSpecTimeboxed(500.millis)(
        Module.looped(equalTo(1)) returns never,
        Module.>.looped(1),
        isNone
      )
  }

  object assertionsComposition {

    def && : Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(3) && isWithin(1, 5)) returns valueF(input => s"foo $input"),
        Module.>.singleParam(3),
        equalTo("foo 3")
      )

    def || : Future[Boolean] =
      testSpec(
        Module.singleParam(equalTo(10) || isWithin(1, 5)) returns valueF(input => s"foo $input"),
        Module.>.singleParam(3),
        equalTo("foo 3")
      )
  }

  object specComposition {

    def *> : Future[Boolean] =
      testSpec(
        (
          (Module.singleParam(equalTo(1)) returns value("foo")) *>
            (Module.static returns value("bar"))
        ),
        Module.>.singleParam(1) *> Module.>.static,
        equalTo("bar")
      )
  }

  object specFailure {

    def invalidArguments: Future[Boolean] =
      testSpecDied(
        Module.command(equalTo(1)) returns unit,
        Module.>.command(2),
        equalTo(InvalidArgumentsException(Module.command, 2, equalTo(1)))
      )

    def invalidMethod: Future[Boolean] =
      testSpecDied(
        Module.command(equalTo(1)) returns unit,
        Module.>.singleParam(1),
        equalTo(
          InvalidMethodException(Module.singleParam, Module.command, equalTo(1))
        )
      )

    def unmetExpectations: Future[Boolean] =
      testSpecDied(
        (
          (Module.command(equalTo(1)) returns unit) *>
            (Module.command(equalTo(2)) returns unit) *>
            (Module.command(equalTo(3)) returns unit)
        ),
        Module.>.command(1),
        equalTo(
          UnmetExpectationsException(
            List(
              Module.command -> equalTo(2),
              Module.command -> equalTo(3)
            )
          )
        )
      )
  }

  private def testSpec[E, A](
    mock: Managed[Nothing, Module],
    app: ZIO[Module, E, A],
    check: Assertion[A]
  ): Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("") {
        val result = mock.use[Any, E, A](app.provide _)
        assertM(result, check)
      }

      succeeded(spec)
    }

  private def testSpecTimeboxed[E, A](duration: Duration)(
    mock: Managed[Nothing, Module],
    app: ZIO[Module, E, A],
    check: Assertion[Option[A]]
  ): Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("") {
        val result =
          mock
            .use(app.provide _)
            .timeout(duration)
            .provide(Clock.Live)

        assertM(result, check)
      }

      succeeded(spec)
    }

  private def testSpecDied[E, A](
    mock: Managed[Nothing, Module],
    app: ZIO[Module, E, A],
    check: Assertion[Throwable]
  ): Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("") {
        val result =
          mock
            .use(app.provide _)
            .orElse(ZIO.unit)
            .absorb
            .flip

        assertM(result, check)
      }

      succeeded(spec)
    }
}

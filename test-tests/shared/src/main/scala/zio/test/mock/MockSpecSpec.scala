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

import zio.{ IO, Managed, ZIO }
import zio.clock.Clock
import zio.duration._
import zio.test.{ assertM, testM, Assertion, Async, ZIOBaseSpec }
import zio.test.Assertion.{ anything, equalTo, isNone, isUnit, isWithin }
import zio.test.TestUtils.{ label, succeeded }
import zio.test.mock.MockSpecUtils.{ intTuple22, Module }
import zio.test.mock.MockException.{ InvalidArgumentsException, InvalidMethodException, UnmetExpectationsException }

object MockSpecSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(expect.singleParam, "expect singleParam"),
    label(expect.manyParams, "expect manyParams"),
    label(expect.manyParamLists, "expect manyParamLists"),
    label(expect.withFailure, "expect withFailure"),
    label(expect.command, "expect command"),
    label(expect.overloaded0, "expect overloaded0"),
    label(expect.overloaded1, "expect overloaded1"),
    label(expect.maxParams, "expect maxParams"),
    label(expect_.singleParam, "expect_ singleParam"),
    label(expect_.manyParams, "expect_ manyParams"),
    label(expect_.manyParamLists, "expect_ manyParamLists"),
    label(expect_.withFailure, "expect_ withFailure"),
    label(expect_.command, "expect_ command"),
    label(expect_.overloaded0, "expect_ overloaded0"),
    label(expect_.overloaded1, "expect_ overloaded1"),
    label(expect_.maxParams, "expect_ maxParams"),
    label(expectAny.static, "expectAny static"),
    label(expectAny.zeroParams, "expectAny zeroParams"),
    label(expectAny.zeroParamsWithParens, "expectAny zeroParamsWithParens"),
    label(expectAny.singleParam, "expectAny singleParam"),
    label(expectAny.manyParams, "expectAny manyParams"),
    label(expectAny.manyParamLists, "expectAny manyParamLists"),
    label(expectAny.withFailure, "expectAny withFailure"),
    label(expectAny.command, "expectAny command"),
    label(expectAny.overloaded0, "expectAny overloaded0"),
    label(expectAny.overloaded1, "expectAny overloaded1"),
    label(expectAny.maxParams, "expectAny maxParams"),
    label(expectAny_.static, "expectAny_ static"),
    label(expectAny_.zeroParams, "expectAny_ zeroParams"),
    label(expectAny_.zeroParamsWithParens, "expectAny_ zeroParamsWithParens"),
    label(expectAny_.singleParam, "expectAny_ singleParam"),
    label(expectAny_.manyParams, "expectAny_ manyParams"),
    label(expectAny_.manyParamLists, "expectAny_ manyParamLists"),
    label(expectAny_.withFailure, "expectAny_ withFailure"),
    label(expectAny_.command, "expectAny_ command"),
    label(expectAny_.overloaded0, "expectAny_ overloaded0"),
    label(expectAny_.overloaded1, "expectAny_ overloaded1"),
    label(expectAny_.maxParams, "expectAny_ maxParams"),
    label(expectAnyM.static, "expectAnyM static"),
    label(expectAnyM.zeroParams, "expectAnyM zeroParams"),
    label(expectAnyM.zeroParamsWithParens, "expectAnyM zeroParamsWithParens"),
    label(expectAnyM.singleParam, "expectAnyM singleParam"),
    label(expectAnyM.manyParams, "expectAnyM manyParams"),
    label(expectAnyM.manyParamLists, "expectAnyM manyParamLists"),
    label(expectAnyM.withFailure, "expectAnyM withFailure"),
    label(expectAnyM.command, "expectAnyM command"),
    label(expectAnyM.never, "expectAnyM never"),
    label(expectAnyM.overloaded0, "expectAnyM overloaded0"),
    label(expectAnyM.overloaded1, "expectAnyM overloaded1"),
    label(expectAnyM.maxParams, "expectAnyM maxParams"),
    label(expectAnyM_.static, "expectAnyM_ static"),
    label(expectAnyM_.zeroParams, "expectAnyM_ zeroParams"),
    label(expectAnyM_.zeroParamsWithParens, "expectAnyM_ zeroParamsWithParens"),
    label(expectAnyM_.singleParam, "expectAnyM_ singleParam"),
    label(expectAnyM_.manyParams, "expectAnyM_ manyParams"),
    label(expectAnyM_.manyParamLists, "expectAnyM_ manyParamLists"),
    label(expectAnyM_.withFailure, "expectAnyM_ withFailure"),
    label(expectAnyM_.command, "expectAnyM_ command"),
    label(expectAnyM_.never, "expectAnyM_ never"),
    label(expectAnyM_.overloaded0, "expectAnyM_ overloaded0"),
    label(expectAnyM_.overloaded1, "expectAnyM_ overloaded1"),
    label(expectAnyM_.maxParams, "expectAnyM_ maxParams"),
    label(expectIn.command, "expectIn command"),
    label(expectM.static, "expectM static"),
    label(expectM.zeroParams, "expectM zeroParams"),
    label(expectM.zeroParamsWithParens, "expectM zeroParamsWithParens"),
    label(expectM.singleParam, "expectM singleParam"),
    label(expectM.manyParams, "expectM manyParams"),
    label(expectM.manyParamLists, "expectM manyParamLists"),
    label(expectM.withFailure, "expectM withFailure"),
    label(expectM.command, "expectM command"),
    label(expectM.never, "expectM never"),
    label(expectM.overloaded0, "expectM overloaded0"),
    label(expectM.overloaded1, "expectM overloaded1"),
    label(expectM.maxParams, "expectM maxParams"),
    label(expectM_.static, "expectM_ static"),
    label(expectM_.zeroParams, "expectM_ zeroParams"),
    label(expectM_.zeroParamsWithParens, "expectM_ zeroParamsWithParens"),
    label(expectM_.singleParam, "expectM_ singleParam"),
    label(expectM_.manyParams, "expectM_ manyParams"),
    label(expectM_.manyParamLists, "expectM_ manyParamLists"),
    label(expectM_.withFailure, "expectM_ withFailure"),
    label(expectM_.command, "expectM_ command"),
    label(expectM_.never, "expectM_ never"),
    label(expectM_.overloaded0, "expectM_ overloaded0"),
    label(expectM_.overloaded1, "expectM_ overloaded1"),
    label(expectM_.maxParams, "expectM_ maxParams"),
    label(expectOut.static, "expectOut static"),
    label(expectOut.zeroParams, "expectOut zeroParams"),
    label(expectOut.zeroParamsWithParens, "expectOut zeroParamsWithParens"),
    label(expectOutM.static, "expectOutM static"),
    label(expectOutM.zeroParams, "expectOutM zeroParams"),
    label(expectOutM.zeroParamsWithParens, "expectOutM zeroParamsWithParens"),
    label(assertionsComposition.&&, "assertions composition &&"),
    label(assertionsComposition.||, "assertions composition ||"),
    label(specComposition.*>, "spec composition *>"),
    label(specComposition.<*, "spec composition <*"),
    label(specFailure.invalidArguments, "spec failure invalid arguments"),
    label(specFailure.invalidMethod, "spec failure invalid method"),
    label(specFailure.unmetExpectations, "spec failure unmet expectations")
  )

  object expect {

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.singleParam)(equalTo(1))(input => s"foo $input"),
        Module.>.singleParam(1),
        equalTo("foo 1")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.manyParams)(equalTo((1, "2", 3L)))(input => s"foo $input"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.manyParamLists)(equalTo((1, "2", 3L)))(input => s"foo $input"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.withFailure)(equalTo(1))(input => s"foo $input"),
        Module.>.withFailure(1),
        equalTo("foo 1")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.command)(equalTo(1))(_ => ()),
        Module.>.command(1),
        isUnit
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.overloaded._0)(equalTo(1))(input => s"foo $input"),
        Module.>.overloaded(1),
        equalTo("foo 1")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.overloaded._1)(equalTo(1L))(input => s"foo $input"),
        Module.>.overloaded(1L),
        equalTo("foo 1")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.maxParams)(equalTo(intTuple22))(input => s"foo $input"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )
  }

  object expect_ {

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.singleParam)(equalTo(1))("foo"),
        Module.>.singleParam(1),
        equalTo("foo")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.manyParams)(equalTo((1, "2", 3L)))("foo"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.manyParamLists)(equalTo((1, "2", 3L)))("foo"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.withFailure)(equalTo(1))("foo"),
        Module.>.withFailure(1),
        equalTo("foo")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.command)(equalTo(1))(()),
        Module.>.command(1),
        isUnit
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.overloaded._0)(equalTo(1))("foo"),
        Module.>.overloaded(1),
        equalTo("foo")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.overloaded._1)(equalTo(1L))("foo"),
        Module.>.overloaded(1L),
        equalTo("foo")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec.expect_(Module.Service.maxParams)(equalTo(intTuple22))("foo"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo")
      )
  }

  object expectAny {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.static)(_ => "foo"),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.zeroParams)(_ => "foo"),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.zeroParamsWithParens)(_ => "foo"),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.singleParam)(input => s"foo $input"),
        Module.>.singleParam(1),
        equalTo("foo 1")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.manyParams)(input => s"foo $input"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.manyParamLists)(input => s"foo $input"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.withFailure)(input => s"foo $input"),
        Module.>.withFailure(1),
        equalTo("foo 1")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.command)(_ => ()),
        Module.>.command(1),
        isUnit
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.overloaded._0)(input => s"foo $input"),
        Module.>.overloaded(1),
        equalTo("foo 1")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.overloaded._1)(input => s"foo $input"),
        Module.>.overloaded(1L),
        equalTo("foo 1")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAny(Module.Service.maxParams)(input => s"foo $input"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )
  }

  object expectAny_ {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.static)("foo"),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.zeroParams)("foo"),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.zeroParamsWithParens)("foo"),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.singleParam)("foo"),
        Module.>.singleParam(1),
        equalTo("foo")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.manyParams)("foo"),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.manyParamLists)("foo"),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.withFailure)("foo"),
        Module.>.withFailure(1),
        equalTo("foo")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.command)(()),
        Module.>.command(1),
        isUnit
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.overloaded._0)("foo"),
        Module.>.overloaded(1),
        equalTo("foo")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.overloaded._1)("foo"),
        Module.>.overloaded(1L),
        equalTo("foo")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAny_(Module.Service.maxParams)("foo"),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo")
      )
  }

  object expectAnyM {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.static)(_ => IO.succeed("foo")),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.zeroParams)(_ => IO.succeed("foo")),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.zeroParamsWithParens)(_ => IO.succeed("foo")),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.singleParam)(input => IO.succeed(s"foo $input")),
        Module.>.singleParam(1),
        equalTo("foo 1")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.manyParams)(input => IO.succeed(s"foo $input")),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.manyParamLists)(input => IO.succeed(s"foo $input")),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.withFailure)(input => IO.succeed(s"foo $input")),
        Module.>.withFailure(1),
        equalTo("foo 1")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.command)(_ => IO.unit),
        Module.>.command(1),
        isUnit
      )

    def never: Future[Boolean] =
      testSpecTimeboxed(500.millis)(
        MockSpec
          .expectAnyM[Any, Unit, Nothing](Module.Service.never)(_ => IO.never)
          .managedEnv[Module]
          .asInstanceOf[Managed[Nothing, Module]],
        Module.>.never(1),
        isNone
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.overloaded._0)(input => IO.succeed(s"foo $input")),
        Module.>.overloaded(1),
        equalTo("foo 1")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.overloaded._1)(input => IO.succeed(s"foo $input")),
        Module.>.overloaded(1L),
        equalTo("foo 1")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM(Module.Service.maxParams)(input => IO.succeed(s"foo $input")),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )
  }

  object expectAnyM_ {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.static)(IO.succeed("foo")),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.zeroParams)(IO.succeed("foo")),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.zeroParamsWithParens)(IO.succeed("foo")),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.singleParam)(IO.succeed("foo")),
        Module.>.singleParam(1),
        equalTo("foo")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.manyParams)(IO.succeed("foo")),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.manyParamLists)(IO.succeed("foo")),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.withFailure)(IO.succeed("foo")),
        Module.>.withFailure(1),
        equalTo("foo")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.command)(IO.unit),
        Module.>.command(1),
        isUnit
      )

    def never: Future[Boolean] =
      testSpecTimeboxed(500.millis)(
        MockSpec
          .expectAnyM_[Any, Unit, Nothing](Module.Service.never)(IO.never)
          .managedEnv[Module]
          .asInstanceOf[Managed[Nothing, Module]],
        Module.>.never(1),
        isNone
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.overloaded._0)(IO.succeed("foo")),
        Module.>.overloaded(1),
        equalTo("foo")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.overloaded._1)(IO.succeed("foo")),
        Module.>.overloaded(1L),
        equalTo("foo")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec.expectAnyM_(Module.Service.maxParams)(IO.succeed("foo")),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo")
      )
  }

  object expectIn {

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectIn(Module.Service.command)(equalTo(1)),
        Module.>.command(1),
        isUnit
      )
  }

  object expectM {

    def static: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.static)(anything)(_ => IO.succeed("foo")),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.zeroParams)(anything)(_ => IO.succeed("foo")),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.zeroParamsWithParens)(anything)(
            _ => IO.succeed("foo")
          ),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec.expectM(Module.Service.singleParam)(equalTo(1))(input => IO.succeed(s"foo $input")),
        Module.>.singleParam(1),
        equalTo("foo 1")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.manyParams)(equalTo((1, "2", 3L)))(input => IO.succeed(s"foo $input")),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo (1,2,3)")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.manyParamLists)(
            equalTo((1, "2", 3L))
          )(input => IO.effectTotal(s"foo $input")),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo (1,2,3)")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.withFailure)(equalTo(1))(
            input => IO.effectTotal(s"foo $input")
          ),
        Module.>.withFailure(1),
        equalTo("foo 1")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectM(Module.Service.command)(equalTo(1))(_ => IO.unit),
        Module.>.command(1),
        isUnit
      )

    def never: Future[Boolean] =
      testSpecTimeboxed(500.millis)(
        MockSpec
          .expectM[Any, Unit, Int, Nothing](Module.Service.never)(equalTo(1))(_ => IO.never)
          .managedEnv[Module]
          .asInstanceOf[Managed[Nothing, Module]],
        Module.>.never(1),
        isNone
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expectM(Module.Service.overloaded._0)(equalTo(1))(input => IO.succeed(s"foo $input")),
        Module.>.overloaded(1),
        equalTo("foo 1")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expectM(Module.Service.overloaded._1)(equalTo(1L))(input => IO.succeed(s"foo $input")),
        Module.>.overloaded(1L),
        equalTo("foo 1")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM(Module.Service.maxParams)(equalTo(intTuple22))(input => IO.succeed(s"foo $input")),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)")
      )
  }

  object expectM_ {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectM_(Module.Service.static)(anything)(IO.succeed("foo")),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectM_(Module.Service.zeroParams)(anything)(IO.succeed("foo")),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectM_(Module.Service.zeroParamsWithParens)(anything)(IO.succeed("foo")),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )

    def singleParam: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM_(Module.Service.singleParam)(equalTo(1))(IO.succeed("foo")),
        Module.>.singleParam(1),
        equalTo("foo")
      )

    def manyParams: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM_(Module.Service.manyParams)(equalTo((1, "2", 3L)))(
            IO.succeed("foo")
          ),
        Module.>.manyParams(1, "2", 3L),
        equalTo("foo")
      )

    def manyParamLists: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM_(Module.Service.manyParamLists)(
            equalTo((1, "2", 3L))
          )(ZIO.succeed("foo")),
        Module.>.manyParamLists(1)("2")(3L),
        equalTo("foo")
      )

    def withFailure: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM_(Module.Service.withFailure)(equalTo(1))(IO.succeed("foo")),
        Module.>.withFailure(1),
        equalTo("foo")
      )

    def command: Future[Boolean] =
      testSpec(
        MockSpec.expectM_(Module.Service.command)(equalTo(1))(IO.unit),
        Module.>.command(1),
        isUnit
      )

    def never: Future[Boolean] =
      testSpecTimeboxed(500.millis)(
        MockSpec
          .expectM_[Any, Unit, Int, Nothing](Module.Service.never)(equalTo(1))(IO.never)
          .managedEnv[Module]
          .asInstanceOf[Managed[Nothing, Module]],
        Module.>.never(1),
        isNone
      )

    def overloaded0: Future[Boolean] =
      testSpec(
        MockSpec.expectM_(Module.Service.overloaded._0)(equalTo(1))(IO.succeed("foo")),
        Module.>.overloaded(1),
        equalTo("foo")
      )

    def overloaded1: Future[Boolean] =
      testSpec(
        MockSpec.expectM_(Module.Service.overloaded._1)(equalTo(1L))(IO.succeed("foo")),
        Module.>.overloaded(1L),
        equalTo("foo")
      )

    def maxParams: Future[Boolean] =
      testSpec(
        MockSpec
          .expectM_(Module.Service.maxParams)(
            equalTo(intTuple22)
          )(IO.succeed("foo")),
        (Module.>.maxParams _).tupled(intTuple22),
        equalTo("foo")
      )
  }

  object expectOut {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectOut(Module.Service.static)("foo"),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectOut(Module.Service.zeroParams)("foo"),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectOut(Module.Service.zeroParamsWithParens)("foo"),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )
  }

  object expectOutM {

    def static: Future[Boolean] =
      testSpec(
        MockSpec.expectOutM(Module.Service.static)(ZIO.succeed("foo")),
        Module.>.static,
        equalTo("foo")
      )

    def zeroParams: Future[Boolean] =
      testSpec(
        MockSpec.expectOutM(Module.Service.zeroParams)(ZIO.succeed("foo")),
        Module.>.zeroParams,
        equalTo("foo")
      )

    def zeroParamsWithParens: Future[Boolean] =
      testSpec(
        MockSpec.expectOutM(Module.Service.zeroParamsWithParens)(ZIO.succeed("foo")),
        Module.>.zeroParamsWithParens(),
        equalTo("foo")
      )
  }

  object assertionsComposition {

    def && : Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.singleParam)(equalTo(3) && isWithin(1, 5))(input => s"foo $input"),
        Module.>.singleParam(3),
        equalTo("foo 3")
      )

    def || : Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.singleParam)(equalTo(10) || isWithin(1, 5))(input => s"foo $input"),
        Module.>.singleParam(3),
        equalTo("foo 3")
      )
  }

  object specComposition {

    def *> : Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.singleParam)(equalTo(1))(input => s"foo $input") *>
          MockSpec.expectIn(Module.Service.command)(equalTo(1)),
        Module.>.singleParam(1) *> Module.>.command(1),
        isUnit
      )

    def <* : Future[Boolean] =
      testSpec(
        MockSpec.expect(Module.Service.singleParam)(equalTo(1))(input => s"foo $input") <*
          MockSpec.expectIn(Module.Service.command)(equalTo(1)),
        Module.>.singleParam(1) <* Module.>.command(1),
        equalTo("foo 1")
      )
  }

  object specFailure {

    def invalidArguments: Future[Boolean] =
      testSpecDied(
        MockSpec.expectIn(Module.Service.command)(equalTo(1)),
        Module.>.command(2),
        equalTo[Throwable](InvalidArgumentsException(Module.Service.command, 2, equalTo(1)))
      )

    def invalidMethod: Future[Boolean] =
      testSpecDied(
        MockSpec.expectIn(Module.Service.command)(equalTo(1)),
        Module.>.singleParam(1),
        equalTo[Throwable](
          InvalidMethodException(Module.Service.singleParam, Expectation(Module.Service.command, equalTo(1)))
        )
      )

    def unmetExpectations: Future[Boolean] =
      testSpecDied(
        (
          MockSpec.expectIn(Module.Service.command)(equalTo(1)) *>
            MockSpec.expectIn(Module.Service.command)(equalTo(2)) *>
            MockSpec.expectIn(Module.Service.command)(equalTo(3))
        ),
        Module.>.command(1),
        equalTo[Throwable](
          UnmetExpectationsException(
            List(
              Expectation(Module.Service.command, equalTo(2)),
              Expectation(Module.Service.command, equalTo(3))
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

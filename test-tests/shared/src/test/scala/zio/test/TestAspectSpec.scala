package zio.test

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils._
import zio.test.environment.TestEnvironment
import zio.{Cause, Ref, ZIO}

object TestAspectSpec
    extends ZIOBaseSpec(
      suite("TestAspectSpec")(
        testM("around evaluates tests inside context of Managed") {
          for {
            ref <- Ref.make(0)
            spec = testM("test") {
              assertM(ref.get, equalTo(1))
            } @@ around(ref.set(1), ref.set(-1))
            result <- succeeded(spec)
            after  <- ref.get
          } yield assert(result && (after == -1), isTrue)
        },
        testM("js applies test aspect only on ScalaJS") {
          for {
            ref    <- Ref.make(false)
            spec   = test("test")(assert(true, isTrue)) @@ js(after(ref.set(true)))
            _      <- execute(spec)
            result <- ref.get
          } yield if (TestPlatform.isJS) assert(result, isTrue) else assert(result, isFalse)
        },
        testM("jsOnly runs tests only on ScalaJS") {
          val spec   = test("Javascript-only")(assert(TestPlatform.isJS, isTrue)) @@ jsOnly
          val result = if (TestPlatform.isJS) succeeded(spec) else ignored(spec)
          result.map(assert(_, isTrue))
        },
        testM("jvm applies test aspect only on jvm") {
          for {
            ref    <- Ref.make(false)
            spec   = test("test")(assert(true, isTrue)) @@ jvm(after(ref.set(true)))
            _      <- execute(spec)
            result <- ref.get
          } yield assert(if (TestPlatform.isJVM) result else !result, isTrue)
        },
        testM("jvmOnly runs tests only on the JVM") {
          val spec   = test("JVM-only")(assert(TestPlatform.isJVM, isTrue)) @@ jvmOnly
          val result = if (TestPlatform.isJVM) succeeded(spec) else ignored(spec)
          result.map(assert(_, isTrue))
        },
        testM("failure makes a test pass if the result was a failure") {
          val spec   = test("test")(assert(throw new java.lang.Exception("boom"), isFalse)) @@ failure
          val result = succeeded(spec)
          result.map(assert(_, isTrue))
        },
        testM("failure makes a test pass if it died with a specified failure") {
          val spec = test("test")(assert(throw new NullPointerException(), isFalse)) @@ failure(
            TestAspectSpecHelper.failsWithException[NullPointerException]
          )
          succeeded(spec).map(assert(_, isTrue))
        },
        testM("failure does not make a test pass if it failed with an unexpected exception") {
          val spec = test("test")(
            assert(throw new NullPointerException(), isFalse)
          ) @@ failure(TestAspectSpecHelper.failsWithException[IllegalArgumentException])
          failed(spec).map(assert(_, isTrue))
        },
        testM("failure does not make a test pass if the specified failure does not match") {
          val spec = test("test")(assert(throw new RuntimeException(), isFalse)) @@ failure(
            isCase[TestFailure[String], Cause[String]]("Runtime", {
              case TestFailure.Runtime(e) => Some(e); case _ => None
            }, equalTo(Cause.fail("boom")))
          )
          failed(spec).map(assert(_, isTrue))
        },
        testM("failure makes tests pass on any assertion failure") {
          val spec = test("test")(assert(true, equalTo(false))) @@ failure
          succeeded(spec).map(assert(_, isTrue))
        },
        testM("failure makes tests pass on an expected assertion failure") {
          val spec = test("test")(assert(true, equalTo(false))) @@ failure(
            isCase[TestFailure[Any], Any]("Assertion", {
              case TestFailure.Assertion(result) => Some(result); case _ => None
            }, anything)
          )
          succeeded(spec).map(assert(_, isTrue))
        },
        testM("timeout makes tests fail after given duration") {
          val spec = (testM("test") {
            assertM(ZIO.never *> ZIO.unit, equalTo(()))                    //todo what is this doing?
          }: ZSpec[TestEnvironment, Any, String, Any]) @@ timeout(1.nanos) //todo confirm type change didn't break anything (was typed ZSpec[Live[Clock], Any, String, Any])
          val result = failedWith(spec, cause => cause == TestTimeoutException("Timeout of 1 ns exceeded."))
          result.map(assert(_, isTrue))
        },
        testM("timeout reports problem with interruption") {
          val spec = (testM("timeoutReportProblemWithInterruption") {
            assertM(ZIO.never.uninterruptible *> ZIO.unit, equalTo(()))
          }: ZSpec[TestEnvironment, Any, String, Any]) @@ timeout(10.millis, 1.nano) //todo confirm type change didn't break anything (was typed ZSpec[Live[Clock], Any, String, Any])
          val result =
            failedWith(
              spec,
              cause =>
                cause == TestTimeoutException(
                  "Timeout of 10 ms exceeded. Couldn't interrupt test within 1 ns, possible resource leak!"
                )
            )
          result.map(assert(_, isTrue))
        }
      )
    )

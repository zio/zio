package zio.test

import scala.reflect.ClassTag
import scala.reflect.ClassTag

import zio.ZEnv
import zio.ZLayer
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils._
import zio.test.environment.{ Live, TestClock }
import zio.{ Ref, Schedule, ZIO }

object TestAspectSpec extends ZIOBaseSpec {

  def spec = suite("TestAspectSpec")(
    testM("around evaluates tests inside context of Managed") {
      for {
        ref <- Ref.make(0)
        spec = testM("test") {
          assertM(ref.get)(equalTo(1))
        } @@ around_(ref.set(1), ref.set(-1))
        result <- isSuccess(spec)
        after  <- ref.get
      } yield {
        assert(result)(isTrue) &&
        assert(after)(equalTo(-1))
      }
    },
    testM("after evaluates in case if test IO fails") {
      for {
        ref <- Ref.make(0)
        spec = testM("test") {
          ZIO.fail("error")
        } @@ after(ref.set(-1))
        result <- isSuccess(spec)
        after  <- ref.get
      } yield {
        assert(result)(isFalse) &&
        assert(after)(equalTo(-1))
      }
    },
    testM("after evaluates in case if test IO dies") {
      for {
        ref <- Ref.make(0)
        spec = testM("test") {
          ZIO.dieMessage("death")
        } @@ after(ref.set(-1))
        result <- isSuccess(spec)
        after  <- ref.get
      } yield {
        assert(result)(isFalse) &&
        assert(after)(equalTo(-1))
      }
    },
    testM("dotty applies test aspect only on Dotty") {
      for {
        ref    <- Ref.make(false)
        spec   = test("test")(assert(true)(isTrue)) @@ dotty(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestVersion.isDotty) assert(result)(isTrue) else assert(result)(isFalse)
    },
    testM("dottyOnly runs tests only on Dotty") {
      val spec   = test("Dotty-only")(assert(TestVersion.isDotty)(isTrue)) @@ dottyOnly
      val result = if (TestVersion.isDotty) isSuccess(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    test("exceptDotty runs tests on all versions except Dotty") {
      assert(TestVersion.isDotty)(isFalse)
    } @@ exceptDotty,
    test("exceptJS runs tests on all platforms except ScalaJS") {
      assert(TestPlatform.isJS)(isFalse)
    } @@ exceptJS,
    test("exceptJVM runs tests on all platforms except the JVM") {
      assert(TestPlatform.isJVM)(isFalse)
    } @@ exceptJVM,
    test("exceptScala2 runs tests on all versions except Scala 2") {
      assert(TestVersion.isScala2)(isFalse)
    } @@ exceptScala2,
    test("failure makes a test pass if the result was a failure") {
      assert(throw new java.lang.Exception("boom"))(isFalse)
    } @@ failure,
    test("failure makes a test pass if it died with a specified failure") {
      assert(throw new NullPointerException())(isFalse)
    } @@ failure(diesWithSubtypeOf[NullPointerException]),
    test("failure does not make a test pass if it failed with an unexpected exception") {
      assert(throw new NullPointerException())(isFalse)
    } @@ failure(diesWithSubtypeOf[IllegalArgumentException])
      @@ failure,
    test("failure does not make a test pass if the specified failure does not match") {
      assert(throw new RuntimeException())(isFalse)
    } @@ failure(diesWith(hasMessage(equalTo("boom"))))
      @@ failure,
    test("failure makes tests pass on any assertion failure") {
      assert(true)(equalTo(false))
    } @@ failure,
    test("failure makes tests pass on an expected assertion failure") {
      assert(true)(equalTo(false))
    } @@ failure(
      isCase[TestFailure[Any], Any](
        "Assertion",
        { case TestFailure.Assertion(result) => Some(result); case _ => None },
        anything
      )
    ),
    testM("flaky retries a test that fails") {
      for {
        ref <- Ref.make(0)
        spec = testM("flaky test") {
          assertM(ref.update(_ + 1))(equalTo(100))
        } @@ flaky
        result <- isSuccess(spec)
        n      <- ref.get
      } yield assert(result)(isTrue) && assert(n)(equalTo(100))
    },
    testM("flaky retries a test that dies") {
      for {
        ref <- Ref.make(0)
        spec = testM("flaky test that dies") {
          assertM(ref.update(_ + 1).filterOrDieMessage(_ >= 100)("die"))(equalTo(100))
        } @@ flaky
        result <- isSuccess(spec)
        n      <- ref.get
      } yield assert(result)(isTrue) && assert(n)(equalTo(100))
    },
    test("flaky retries a test with a limit") {
      assert(true)(isFalse)
    } @@ flaky @@ failure,
    test("ifEnv runs a test if environment variable satisfies assertion") {
      assert(true)(isTrue)
    } @@ ifEnv("PATH", containsString("bin")) @@ success @@ jvmOnly,
    test("ifEnv ignores a test if environment variable does not satisfy assertion") {
      assert(true)(isFalse)
    } @@ ifEnv("PATH", nothing) @@ jvmOnly,
    test("ifEnv ignores a test if environment variable does not exist") {
      assert(true)(isFalse)
    } @@ ifEnv("QWERTY", anything) @@ jvmOnly,
    test("ifEnvSet runs a test if environment variable is set") {
      assert(true)(isTrue)
    } @@ ifEnvSet("PATH") @@ success @@ jvmOnly,
    test("ifEnvSet ignores a test if environment variable is not set") {
      assert(true)(isFalse)
    } @@ ifEnvSet("QWERTY") @@ jvmOnly,
    test("ifProp runs a test if property satisfies assertion") {
      assert(true)(isTrue)
    } @@ ifProp("java.vm.name", containsString("VM")) @@ success @@ jvmOnly,
    test("ifProp ignores a test if property does not satisfy assertion") {
      assert(true)(isFalse)
    } @@ ifProp("java.vm.name", nothing) @@ jvmOnly,
    test("ifProp ignores a test if property does not exist") {
      assert(true)(isFalse)
    } @@ ifProp("qwerty", anything) @@ jvmOnly,
    test("ifPropSet runs a test if property is set") {
      assert(true)(isTrue)
    } @@ ifPropSet("java.vm.name") @@ success @@ jvmOnly,
    test("ifPropSet ignores a test if property is not set") {
      assert(true)(isFalse)
    } @@ ifPropSet("qwerty") @@ jvmOnly,
    testM("js applies test aspect only on ScalaJS") {
      for {
        ref    <- Ref.make(false)
        spec   = test("test")(assert(true)(isTrue)) @@ js(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestPlatform.isJS) assert(result)(isTrue) else assert(result)(isFalse)
    },
    testM("jsOnly runs tests only on ScalaJS") {
      val spec   = test("Javascript-only")(assert(TestPlatform.isJS)(isTrue)) @@ jsOnly
      val result = if (TestPlatform.isJS) isSuccess(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    testM("jvm applies test aspect only on jvm") {
      for {
        ref    <- Ref.make(false)
        spec   = test("test")(assert(true)(isTrue)) @@ jvm(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield assert(if (TestPlatform.isJVM) result else !result)(isTrue)
    },
    testM("jvmOnly runs tests only on the JVM") {
      val spec   = test("JVM-only")(assert(TestPlatform.isJVM)(isTrue)) @@ jvmOnly
      val result = if (TestPlatform.isJVM) isSuccess(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    suite("nonTermination")(
      testM("makes a test pass if it does not terminate within the specified time") {
        assertM(ZIO.never)(anything)
      } @@ nonTermination(10.milliseconds),
      testM("makes a test fail if it succeeds within the specified time") {
        assertM(ZIO.unit)(anything)
      } @@ nonTermination(1.minute) @@ failure,
      testM("makes a test fail if it fails within the specified time") {
        assertM(ZIO.fail("fail"))(anything)
      } @@ nonTermination(1.minute) @@ failure
    ),
    testM("retry retries failed tests according to a schedule") {
      for {
        ref <- Ref.make(0)
        spec = testM("retry") {
          assertM(ref.update(_ + 1))(equalTo(2))
        } @@ retry(Schedule.recurs(1))
        result <- isSuccess(spec)
      } yield assert(result)(isTrue)
    },
    testM("scala2 applies test aspect only on Scala 2") {
      for {
        ref    <- Ref.make(false)
        spec   = test("test")(assert(true)(isTrue)) @@ scala2(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestVersion.isScala2) assert(result)(isTrue) else assert(result)(isFalse)
    },
    testM("scala2Only runs tests only on Scala 2") {
      val spec   = test("Scala2-only")(assert(TestVersion.isScala2)(isTrue)) @@ scala2Only
      val result = if (TestVersion.isScala2) isSuccess(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    testM("timeout makes tests fail after given duration") {
      assertM(ZIO.never *> ZIO.unit)(equalTo(()))
    } @@ timeout(1.nanos)
      @@ failure(diesWithSubtypeOf[TestTimeoutException]),
    testM("timeout reports problem with interruption") {
      for {
        testClock <- ZIO.environment[TestClock].map(_.get[TestClock.Service])
        liveClock = (ZEnv.live >>> Live.default) ++ ZLayer.succeed(testClock)
        spec = testM("uninterruptible test") {
          for {
            _ <- (TestClock.adjust(11.milliseconds) *> ZIO.never).uninterruptible
          } yield assertCompletes
        } @@ timeout(10.milliseconds, 1.nanosecond) @@ failure(diesWith(equalTo(interruptionTimeoutFailure)))
        result <- isSuccess(spec.provideLayer(liveClock))
      } yield assert(result)(isTrue)
    }
  )

  def diesWithSubtypeOf[E](implicit ct: ClassTag[E]): Assertion[TestFailure[E]] =
    diesWith(isSubtype[E](anything))

  def diesWith(assertion: Assertion[Throwable]): Assertion[TestFailure[Any]] =
    isCase(
      "Runtime", {
        case TestFailure.Runtime(c) => c.dieOption
        case _                      => None
      },
      assertion
    )

  val interruptionTimeoutFailure =
    TestTimeoutException(
      "Timeout of 10 ms exceeded. Couldn't interrupt test within 1 ns, possible resource leak!"
    )
}

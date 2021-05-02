package zio.test

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils._
import zio.test.environment.TestRandom
import zio.{Ref, TracingStatus, ZIO}

import scala.reflect.ClassTag

object TestAspectSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("TestAspectSpec")(
    testM("around evaluates tests inside context of Managed") {
      for {
        ref <- Ref.make(0)
        spec = testM("test") {
                 assertM(ref.get)(equalTo(1))
               } @@ around_(ref.set(1), ref.set(-1))
        result <- succeeded(spec)
        after  <- ref.get
      } yield {
        assert(result) &&
        assert(after)(equalTo(-1))
      }
    },
    testM("aroundAll evaluates all tests between before and after effects") {
      for {
        ref <- Ref.make(0)
        spec = suite("suite1")(
                 suite("suite2")(
                   testM("test1") {
                     assertM(ref.get)(equalTo(1))
                   },
                   testM("test2") {
                     assertM(ref.get)(equalTo(1))
                   },
                   testM("test3") {
                     assertM(ref.get)(equalTo(1))
                   }
                 ) @@ aroundAll_(ref.update(_ + 1), ref.update(_ - 1)),
                 testM("test4") {
                   assertM(ref.get)(equalTo(1))
                 } @@ aroundAll_(ref.update(_ + 1), ref.update(_ - 1))
               )
        result <- succeeded(spec)
        after  <- ref.get
      } yield {
        assert(result) &&
        assert(after)(equalTo(0))
      }
    },
    testM("after evaluates in case if test IO fails") {
      for {
        ref <- Ref.make(0)
        spec = testM("test") {
                 ZIO.fail("error")
               } @@ after(ref.set(-1))
        result <- succeeded(spec)
        after  <- ref.get
      } yield {
        assert(!result) &&
        assert(after)(equalTo(-1))
      }
    },
    testM("after evaluates in case if test IO dies") {
      for {
        ref <- Ref.make(0)
        spec = testM("test") {
                 ZIO.dieMessage("death")
               } @@ after(ref.set(-1))
        result <- succeeded(spec)
        after  <- ref.get
      } yield {
        assert(!result) &&
        assert(after)(equalTo(-1))
      }
    },
    testM("dotty applies test aspect only on Dotty") {
      for {
        ref    <- Ref.make(false)
        spec    = test("test")(assert(true)) @@ dotty(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestVersion.isDotty) assert(result) else assert(!result)
    },
    testM("dottyOnly runs tests only on Dotty") {
      val spec   = test("Dotty-only")(assert(TestVersion.isDotty)) @@ dottyOnly
      val result = if (TestVersion.isDotty) succeeded(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    test("exceptDotty runs tests on all versions except Dotty") {
      assert(!TestVersion.isDotty)
    } @@ exceptDotty,
    test("exceptJS runs tests on all platforms except ScalaJS") {
      assert(!TestPlatform.isJS)
    } @@ exceptJS,
    test("exceptJVM runs tests on all platforms except the JVM") {
      assert(!TestPlatform.isJVM)
    } @@ exceptJVM,
    test("exceptNative runs tests on all platforms except ScalaNative") {
      assert(!TestPlatform.isNative)
    } @@ exceptNative,
    test("exceptScala2 runs tests on all versions except Scala 2") {
      assert(!TestVersion.isScala2)
    } @@ exceptScala2,
    test("failure makes a test pass if the result was a failure") {
      assert(throw new java.lang.Exception("boom"))
    } @@ failing,
    test("failure makes a test pass if it died with a specified failure") {
      assert(throw new NullPointerException())
    } @@ failing(diesWithSubtypeOf[NullPointerException]),
    test("failure does not make a test pass if it failed with an unexpected exception") {
      assert(throw new NullPointerException())
    } @@ failing(diesWithSubtypeOf[IllegalArgumentException])
      @@ failing,
    test("failure does not make a test pass if the specified failure does not match") {
      assert(throw new RuntimeException())
    } @@ failing(diesWith(hasMessage(equalTo("boom"))))
      @@ failing,
    test("failure makes tests pass on any assertion failure") {
      assert(false)
    } @@ failing,
    test("failure makes tests pass on an expected assertion failure") {
      assert(false)
    } @@ failing(
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
                 assertM(ref.updateAndGet(_ + 1))(equalTo(100))
               } @@ flaky
        result <- succeeded(spec)
        n      <- ref.get
      } yield assert(result) && assert(n)(equalTo(100))
    },
    testM("flaky retries a test that dies") {
      for {
        ref <- Ref.make(0)
        spec = testM("flaky test that dies") {
                 assertM(ref.updateAndGet(_ + 1).filterOrDieMessage(_ >= 100)("die"))(equalTo(100))
               } @@ flaky
        result <- succeeded(spec)
        n      <- ref.get
      } yield assert(result) && assert(n)(equalTo(100))
    },
    test("flaky retries a test with a limit") {
      assert(false)
    } @@ flaky @@ failing,
    test("ifEnv runs a test if environment variable satisfies assertion") {
      assert(true)
    } @@ ifEnv("PATH", containsString("bin")) @@ success @@ jvmOnly,
    test("ifEnv ignores a test if environment variable does not satisfy assertion") {
      assert(false)
    } @@ ifEnv("PATH", nothing) @@ jvmOnly,
    test("ifEnv ignores a test if environment variable does not exist") {
      assert(false)
    } @@ ifEnv("QWERTY", anything) @@ jvmOnly,
    test("ifEnvSet runs a test if environment variable is set") {
      assert(true)
    } @@ ifEnvSet("PATH") @@ success @@ jvmOnly,
    test("ifEnvSet ignores a test if environment variable is not set") {
      assert(false)
    } @@ ifEnvSet("QWERTY") @@ jvmOnly,
    test("ifProp runs a test if property satisfies assertion") {
      assert(true)
    } @@ ifProp("java.vm.name", containsString("VM")) @@ success @@ jvmOnly,
    test("ifProp ignores a test if property does not satisfy assertion") {
      assert(false)
    } @@ ifProp("java.vm.name", nothing) @@ jvmOnly,
    test("ifProp ignores a test if property does not exist") {
      assert(false)
    } @@ ifProp("qwerty", anything) @@ jvmOnly,
    test("ifPropSet runs a test if property is set") {
      assert(true)
    } @@ ifPropSet("java.vm.name") @@ success @@ jvmOnly,
    test("ifPropSet ignores a test if property is not set") {
      assert(false)
    } @@ ifPropSet("qwerty") @@ jvmOnly,
    testM("js applies test aspect only on ScalaJS") {
      for {
        ref    <- Ref.make(false)
        spec    = test("test")(assert(true)) @@ js(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestPlatform.isJS) assert(result) else assert(!result)
    },
    testM("jsOnly runs tests only on ScalaJS") {
      val spec   = test("Javascript-only")(assert(TestPlatform.isJS)) @@ jsOnly
      val result = if (TestPlatform.isJS) succeeded(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    testM("jvm applies test aspect only on jvm") {
      for {
        ref    <- Ref.make(false)
        spec    = test("test")(assert(true)) @@ jvm(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield assert(if (TestPlatform.isJVM) result else !result)
    },
    testM("jvmOnly runs tests only on the JVM") {
      val spec   = test("JVM-only")(assert(TestPlatform.isJVM)) @@ jvmOnly
      val result = if (TestPlatform.isJVM) succeeded(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    testM("native applies test aspect only on ScalaNative") {
      for {
        ref    <- Ref.make(false)
        spec    = test("test")(assert(true)) @@ native(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestPlatform.isNative) assert(result) else assert(!result)
    },
    testM("nativeOnly runs tests only on ScalaNative") {
      val spec   = test("Native-only")(assert(TestPlatform.isNative)) @@ nativeOnly
      val result = if (TestPlatform.isNative) succeeded(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    suite("nonTermination")(
      testM("makes a test pass if it does not terminate within the specified time") {
        assertM(ZIO.never)(anything)
      } @@ nonTermination(10.milliseconds),
      testM("makes a test fail if it succeeds within the specified time") {
        assertM(ZIO.unit)(anything)
      } @@ nonTermination(1.minute) @@ failing,
      testM("makes a test fail if it fails within the specified time") {
        assertM(ZIO.fail("fail"))(anything)
      } @@ nonTermination(1.minute) @@ failing
    ),
    testM("repeats sets the number of times to repeat a test to the specified value") {
      for {
        ref   <- Ref.make(0)
        spec   = testM("test")(assertM(ref.update(_ + 1))(anything)) @@ nonFlaky @@ repeats(42)
        _     <- execute(spec)
        value <- ref.get
      } yield assert(value)(equalTo(43))
    },
    testM("repeats sets the number of times to repeat a test to the specified value") {
      for {
        ref   <- Ref.make(0)
        spec   = testM("test")(assertM(ref.update(_ + 1))(nothing)) @@ flaky @@ retries(42)
        _     <- execute(spec)
        value <- ref.get
      } yield assert(value)(equalTo(43))
    },
    testM("samples sets the number of sufficient samples to the specified value") {
      for {
        ref   <- Ref.make(0)
        _     <- checkM(Gen.anyInt.noShrink)(_ => assertM(ref.update(_ + 1))(anything))
        value <- ref.get
      } yield assert(value)(equalTo(42))
    } @@ samples(42),
    testM("scala2 applies test aspect only on Scala 2") {
      for {
        ref    <- Ref.make(false)
        spec    = test("test")(assert(true)) @@ scala2(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestVersion.isScala2) assert(result) else assert(!result)
    },
    testM("scala2Only runs tests only on Scala 2") {
      val spec   = test("Scala2-only")(assert(TestVersion.isScala2)) @@ scala2Only
      val result = if (TestVersion.isScala2) succeeded(spec) else isIgnored(spec)
      assertM(result)(isTrue)
    },
    testM("setSeed sets the random seed to the specified value before each test") {
      assertM(TestRandom.getSeed)(equalTo(seed & ((1L << 48) - 1)))
    } @@ setSeed(seed),
    testM("shrinks sets the maximum number of shrinkings to the specified value") {
      for {
        ref   <- Ref.make(0)
        _     <- checkM(Gen.anyInt)(_ => assertM(ref.update(_ + 1))(nothing))
        value <- ref.get
      } yield assert(value)(equalTo(1))
    } @@ shrinks(0),
    testM("shrinks preserves the original failure") {
      check(Gen.anyInt) { n =>
        assert(n)(equalTo(n + 1))
      }
    } @@ shrinks(0) @@ failing,
    testM("sized sets the size to the specified value") {
      assertM(Sized.size)(equalTo(42))
    } @@ sized(42),
    testM("timeout makes tests fail after given duration") {
      assertM(ZIO.never *> ZIO.unit)(equalTo(()))
    } @@ timeout(1.nanos)
      @@ failing(diesWithSubtypeOf[TestTimeoutException]),
    testM("verify verifies the specified post-condition after each test is run") {
      for {
        ref <- Ref.make(false)
        spec = suite("verify")(
                 testM("first test")(ZIO.succeed(assertCompletes)),
                 testM("second test")(ref.set(true).as(assertCompletes))
               ) @@ sequential @@ verify(assertM(ref.get)(isTrue))
        result <- succeeded(spec)
      } yield assert(!result)
    },
    testM("untraced disables tracing") {
      assertM(ZIO.checkTraced(ZIO.succeed(_)))(equalTo(TracingStatus.Untraced))
    } @@ untraced
  )

  def diesWithSubtypeOf[E](implicit ct: ClassTag[E]): Assertion[TestFailure[E]] =
    diesWith(isSubtype[E](anything))

  def diesWith(assertion: Assertion[Throwable]): Assertion[TestFailure[Any]] =
    isCase(
      "Runtime",
      {
        case TestFailure.Runtime(c) => c.dieOption
        case _                      => None
      },
      assertion
    )

  val interruptionTimeoutFailure: TestTimeoutException =
    TestTimeoutException(
      "Timeout of 10 ms exceeded. Couldn't interrupt test within 1 ns, possible resource leak!"
    )

  val seed = -1157790455010312737L
}

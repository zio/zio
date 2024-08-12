package zio

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import java.io.{ByteArrayOutputStream, PrintStream}

object FiberFailureSpec extends ZIOBaseSpec {

  val expectedStackTrace = Seq(
    "zio.FiberFailure.<init>",
    "zio.FiberFailure$.apply",
    "zio.Exit.getOrThrowFiberFailure",
    "zio.internal.FiberRuntime.runLoop"
  )

  def spec = suite("FiberFailureSpec")(
    test("FiberFailure getStackTrace includes relevant ZIO stack traces") {
      val exception    = new Exception("Test Exception")
      val fiberFailure = FiberFailure(Cause.fail(exception))
      val stackTrace   = fiberFailure.getStackTrace.map(_.toString).mkString("\n")

      assertTrue(stackTrace.contains(expectedStackTrace))
    },
    test("FiberFailure toString should match cause.prettyPrint") {
      val cause        = Cause.fail(new Exception("Test Exception"))
      val fiberFailure = FiberFailure(cause)

      assert(fiberFailure.toString)(equalTo(cause.prettyPrint))
    },
    test("FiberFailure printStackTrace should correctly output the stack trace") {
      val cause        = Cause.fail(new Exception("Test Exception"))
      val fiberFailure = FiberFailure(cause)

      val outputStream = new ByteArrayOutputStream()
      val printStream  = new PrintStream(outputStream)

      fiberFailure.printStackTrace(printStream)

      val stackTraceOutput = new String(outputStream.toByteArray)

      assertTrue(
        stackTraceOutput.contains("FiberFailure"),
        stackTraceOutput.contains("Test Exception")
      )
    },
    test("FiberFailure captures the stack trace for ZIO.fail with String") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail("boom")).getOrThrowFiberFailure()
        }
      def call1(): Unit = subcall()

      val fiberFailureTest = ZIO
        .attempt(call1())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTrace = fiberFailure.getStackTrace.mkString("\n")
            ZIO.log(s"Captured Stack Trace:\n$stackTrace") *>
              ZIO.succeed(stackTrace)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      fiberFailureTest.flatMap { stackTrace =>
        ZIO.succeed {
          assertTrue(
            stackTrace.contains("call1") &&
              stackTrace.contains("subcall") &&
              stackTrace.contains(expectedStackTrace)
          )
        }
      }
    },
    test("FiberFailure captures the stack trace for ZIO.fail with Throwable") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail(new Exception("boom"))).getOrThrowFiberFailure()
        }
      def call1(): Unit = subcall()

      val fiberFailureTest = ZIO
        .attempt(call1())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTrace = fiberFailure.getStackTrace.mkString("\n")
            ZIO.log(s"Captured Stack Trace:\n$stackTrace") *>
              ZIO.succeed(stackTrace)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      fiberFailureTest.flatMap { stackTrace =>
        ZIO.succeed {
          assertTrue(
            stackTrace.contains("call1") &&
              stackTrace.contains("subcall") &&
              stackTrace.contains(expectedStackTrace)
          )
        }
      }
    },
    test("FiberFailure captures the stack trace for ZIO.die") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.die(new RuntimeException("boom"))).getOrThrowFiberFailure()
        }
      def call1(): Unit = subcall()

      val fiberFailureTest = ZIO
        .attempt(call1())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTrace = fiberFailure.getStackTrace.mkString("\n")
            ZIO.log(s"Captured Stack Trace:\n$stackTrace") *>
              ZIO.succeed(stackTrace)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      fiberFailureTest.flatMap { stackTrace =>
        ZIO.succeed {
          assertTrue(
            stackTrace.contains("call1") &&
              stackTrace.contains("subcall") &&
              stackTrace.contains(expectedStackTrace)
          )
        }
      }
    }
  ) @@ exceptJS
}

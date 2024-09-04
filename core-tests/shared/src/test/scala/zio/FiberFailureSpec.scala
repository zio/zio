package zio

import zio.test._
import zio.test.TestAspect._
import java.io.{ByteArrayOutputStream, PrintStream}

object FiberFailureSpec extends ZIOBaseSpec {

  val expectedStackTraceElements = Seq(
    "FiberFailure",
    "apply",
    "getOrThrowFiberFailure"
  )

  def spec = suite("FiberFailureSpec")(
    test("FiberFailure getStackTrace includes relevant ZIO stack traces") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail("boom")).getOrThrowFiberFailure()
        }

      val stackTrace = ZIO
        .attempt(subcall())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTraceStr = fiberFailure.getStackTrace.map(_.toString).mkString("\n")
            ZIO.succeed(stackTraceStr)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      stackTrace.flatMap { trace =>
        ZIO.succeed {
          assertTrue(expectedStackTraceElements.forall(element => trace.contains(element)))
        }
      }
    },
    test("FiberFailure toString should include cause and stack trace") {
      val cause        = Cause.fail(new Exception("Test Exception"))
      val fiberFailure = FiberFailure(cause)

      val toStringOutput = fiberFailure.toString

      assertTrue(
        toStringOutput.contains("Test Exception"),
        // General check for stack trace
        toStringOutput.contains("at")
      )
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
              expectedStackTraceElements.forall(element => stackTrace.contains(element))
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
              expectedStackTraceElements.forall(element => stackTrace.contains(element))
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
              expectedStackTraceElements.forall(element => stackTrace.contains(element))
          )
        }
      }
    },
    test("getStackTrace, toString, and printStackTrace should produce identical stack traces") {

      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail("boom")).getOrThrowFiberFailure()
        }

      val result = ZIO
        .attempt(subcall())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTraceFromGetStackTrace = fiberFailure.getStackTrace.mkString("\n")
            val stackTraceFromToString      = fiberFailure.toString
            val stackTraceFromPrint = {
              val baos = new ByteArrayOutputStream()
              try {
                fiberFailure.printStackTrace(new PrintStream(baos))
                baos.toString
              } finally {
                baos.close()
              }
            }

            // Logging for review
            ZIO.log(s"Captured Stack Trace from getStackTrace:\n$stackTraceFromGetStackTrace") *>
              ZIO.log(s"Captured toString Output:\n$stackTraceFromToString") *>
              ZIO.log(s"Captured Stack Trace from printStackTrace:\n$stackTraceFromPrint") *>
              ZIO.succeed((stackTraceFromGetStackTrace, stackTraceFromToString, stackTraceFromPrint))
          case other =>
            ZIO.fail(new RuntimeException(s"Unexpected failure: ${other.getMessage}"))
        }
        .asInstanceOf[ZIO[Any, Nothing, (String, String, String)]]

      result.flatMap { case (stackTraceFromGetStackTrace, stackTraceFromToString, stackTraceFromPrint) =>
        // Expected stack trace format (before normalisation)
        // val expectedStackTrace =
        //   """Exception in thread "zio-fiber" java.lang.String: boom
        //     |	at zio.FiberFailureSpec.spec.subcall(FiberFailureSpec.scala:152)
        //     |Stack trace:
        //     |	at zio.FiberFailureSpec.spec.subcall(FiberFailureSpec.scala:152)
        //     |	at zio.Exit.$anonfun$getOrThrowFiberFailure$1(ZIO.scala:6469)
        //     |	at zio.Exit.getOrElse(ZIO.scala:6462)
        //     |	at zio.Exit.getOrElse$(ZIO.scala:6460)
        //     |	at zio.Exit$Failure.getOrElse(ZIO.scala:6665)
        //     |	at zio.Exit.getOrThrowFiberFailure(ZIO.scala:6469)
        //     |	at zio.Exit.getOrThrowFiberFailure$(ZIO.scala:6468)
        //     |	at zio.Exit$Failure.getOrThrowFiberFailure(ZIO.scala:6665)
        //     |	at zio.FiberFailureSpec$.$anonfun$spec$64(FiberFailureSpec.scala:152)
        //     |	at zio.Unsafe$.unsafe(Unsafe.scala:37)
        //     |	at zio.FiberFailureSpec$.subcall$5(FiberFailureSpec.scala:151)
        //     |	at zio.FiberFailureSpec$.$anonfun$spec$66(FiberFailureSpec.scala:156)
        //     |	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
        //     |	at zio.ZIOCompanionVersionSpecific.$anonfun$attempt$1(ZIOCompanionVersionSpecific.scala:100)""".stripMargin
        val normalizedGetStackTrace   = normalizeStackTrace(stackTraceFromGetStackTrace)
        val normalizedToString        = normalizeStackTraceWithCauseFilter(stackTraceFromToString)
        val normalizedPrintStackTrace = normalizeStackTraceWithCauseFilter(stackTraceFromPrint)

        // Logging the normalized stack traces for review
        ZIO.log(s"Normalized Stack Trace from getStackTrace:\n$normalizedGetStackTrace") *>
          ZIO.log(s"Normalized toString Output:\n$normalizedToString") *>
          ZIO.log(s"Normalized Stack Trace from printStackTrace:\n$normalizedPrintStackTrace") *>
          ZIO.succeed {
            assertTrue(
              normalizedGetStackTrace == normalizedToString &&
                normalizedToString == normalizedPrintStackTrace
            )
          }
      }
    }
  ) @@ exceptJS

  // Helper method for normalizing stack traces
  private def normalizeStackTrace(stackTrace: String): String =
    stackTrace
      .split("\n")
      .map { line =>
        line.trim
          .replaceAll("""\([^)]*\)""", "")
          .replaceAll("""^\s*Exception in thread \".*\" """, "")
          .replaceAll("""^(?!at\s)(.+)""", "at $1")
          .replaceAll("""\s+""", " ")
      }
      .distinct
      .filterNot(_.isEmpty)
      .mkString("\n")

  // Helper method to filter out the cause and normalize the remaining stack trace
  private def normalizeStackTraceWithCauseFilter(trace: String): String = {
    val filteredTrace = trace
      .split("\n")
      .dropWhile(line => line.contains("boom") || line.contains("Exception in thread"))

    normalizeStackTrace(filteredTrace.mkString("\n"))
  }
}

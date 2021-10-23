package zio

import zio.ZIO.attemptBlocking
import zio.test.Assertion._
import zio.test.AssertionM.RenderParam
import zio.test._

import java.io.{PrintWriter, StringWriter}

object RuntimeSpec extends ZIOBaseSpec {
  private val runtime = Runtime.default
  override def spec: ZSpec[Environment, Failure] = suite("RuntimeSpec")(
    suite("By default include stack trace up to unsafeRun* in Fiber Trace")(
      test("in unsafeRunTask") {
        for {
          res <- attemptBlocking(CallSite.failingUnsafeRunTask(runtime)).exit
        } yield {
          assert(res)(
            fails(
              hasField(
                "stackTrace",
                stackTraceSanitized,
                containsLinesInOrder(
                  // the exception
                  "java.lang.RuntimeException: kaboom!",
                  "at zio.RuntimeSpec$EffectSite$.$anonfun$failing$3(XXX)",
                  "java.lang.RuntimeException: kaboom!",
                  // fiber trace of the effect
                  "at zio.RuntimeSpec.EffectSite.failing(XXX)",
                  "at zio.RuntimeSpec.EffectSite.failing(XXX)",
                  "at zio.RuntimeSpec.EffectSite.failing(XXX)",
                  // parent stack trace - the stack up to unsafe run
                  "Fiber:FiberId(0,0) execution trace:",
                  "at zio.Runtime$$anon$3.unsafeRunTask(XXX)",
                  "at zio.RuntimeSpec$CallSite$.run(XXX)",
                  "at zio.RuntimeSpec$CallSite$.failingUnsafeRunTask(XXX)"
                )
              )
            )
          )
        }
      },
      test("in unsafeRunToFuture") {
        for {
          res <- ZIO.fromFuture(_ => CallSite.failingUnsafeRunToFuture(runtime)).exit
        } yield {
          assert(res)(
            fails(
              hasField(
                "stackTrace",
                stackTraceSanitized,
                containsLinesInOrder(
                  "java.lang.RuntimeException: kaboom!",
                  "at zio.RuntimeSpec$EffectSite$.$anonfun$failing$3(XXX)",
                  "java.lang.RuntimeException: kaboom!",
                  // fiber trace of the effect
                  "at zio.RuntimeSpec.EffectSite.failing(XXX)",
                  "at zio.RuntimeSpec.EffectSite.failing(XXX)",
                  "at zio.RuntimeSpec.EffectSite.failing(XXX)",
                  // parent stack trace - the stack up to unsafe run
                  "Fiber:FiberId(0,0) execution trace:",
                  "at zio.Runtime$$anon$3.unsafeRunToFuture(XXX)",
                  "at zio.RuntimeSpec$CallSite$.run(XXX)",
                  "at zio.RuntimeSpec$CallSite$.failingUnsafeRunToFuture(XXX)"
                )
              )
            )
          )
        }
      }
    ),
    suite("when captureUnsafeRunStack = false, don't capture stack")(
      test("in unsafeRunTask") {
        for {
          res <- attemptBlocking(
                   CallSite.failingUnsafeRunTask(runtime.withTracingConfig(_.withCaptureUnsafeRunStack(false)))
                 ).exit
        } yield {
          assert(res)(
            fails(
              hasField(
                "stackTrace",
                stackTraceSanitized,
                not(containsString("at zio.RuntimeSpec$CallSite$.run(XXX)"))
              )
            )
          )
        }
      },
      test("in unsafeRunToFuture") {
        for {
          res <- ZIO
                   .fromFuture(_ =>
                     CallSite.failingUnsafeRunToFuture(runtime.withTracingConfig(_.withCaptureUnsafeRunStack(false)))
                   )
                   .exit
        } yield {
          assert(res)(
            fails(
              hasField(
                "stackTrace",
                stackTraceSanitized,
                not(containsString("at zio.RuntimeSpec$CallSite$.run(XXX)"))
              )
            )
          )
        }
      }
    )
  )

  private def stackTraceSanitized(e: Throwable) = {
    val writer = new StringWriter()
    e.printStackTrace(new PrintWriter(writer))
    writer.toString
      .split("\n")
      .map(_.replaceAll("\\(.*\\)$", "(XXX)").trim)
      .mkString("\n")
  }

  private def containsLinesInOrder(lines: String*): Assertion[String] =
    Assertion.assertion("containsLinesInOrder")(RenderParam.Value(lines)) { text =>
      val filtered = text.split("\n").toSeq.filter(l => lines.contains(l.trim))
      filtered.containsSlice(lines)
    }

  private object CallSite {

    private def run[T](t: => T): T = t

    def failingUnsafeRunTask(rt: Runtime[Has[Clock]]) =
      run(rt.unsafeRunTask(EffectSite.failing))

    def failingUnsafeRunToFuture(rt: Runtime[Has[Clock]]) =
      run(rt.unsafeRunToFuture(EffectSite.failing))
  }

  private object EffectSite {
    val failing = ZIO.sleep(10.millis) *>
      ZIO.fail(new RuntimeException("kaboom!")).unit
  }
}

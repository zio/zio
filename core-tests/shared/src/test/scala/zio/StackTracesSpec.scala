package zio

import zio.test.{ZSpec, assertTrue}

object StackTracesSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackTracesSpec")(
    suite("captureSimpleCause")(
      test("captures a simple failure") {
        val failureStackTraceContent: IO[String, Nothing] => ZIO[Any, Serializable, String] = {
          case fail: ZIO[Any, String, Unit] =>
            fail.catchAllCause {
              case c: Cause[String] => ZIO(c.prettyPrint)
              case _                => UnsupportedTestPath
            }
          case _ => UnsupportedTestPath
        }
        val checkExpectations: String => Boolean = {
          case stack: String =>
            stack.startsWith("Exception in thread") && includesAll(Seq("zio-fiber", "java.lang.String: Oh no!"))(stack)
          case _ => false
        }
        for {
          _          <- ZIO.succeed(15)
          stackTrace <- failureStackTraceContent(ZIO.fail("Oh no!"))
          result      = checkExpectations(stackTrace)
        } yield assertTrue(result)
      }
    )
  )

  private def includesAll(texts: Seq[String]): String => Boolean = stack => texts.map(stack.contains).forall(r => r)

  private val UnsupportedTestPath = ZIO.fail("not considered scenario")
}

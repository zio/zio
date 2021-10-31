package zio

import zio.test.{ZSpec, assertTrue}

object StackTracesSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackTracesSpec")(
    suite("captureSimpleCause")(
      test("captures a simple failure") {
        for {
          _ <- ZIO.succeed(15)
          stackTrace <- ZIO.fail("Oh no!") match {
                          case fail: ZIO[Any, String, Nothing] =>
                            fail.catchAllCause {
                              case c: Cause[String] => ZIO(c.prettyPrint)
                              case _                => UnsupportedTestPath
                            }
                          case _ => UnsupportedTestPath
                        }
        } yield {
          assertTrue(stackTrace.startsWith("Exception in thread")) &&
          assertTrue(includesAll(Seq("zio-fiber", "java.lang.String: Oh no!"))(stackTrace))
        }
      }
    )
  )

  private def includesAll(texts: Seq[String]): String => Boolean = stack => texts.map(stack.contains).forall(r => r)

  private val UnsupportedTestPath: Task[String] = ZIO("not considered scenario")
}

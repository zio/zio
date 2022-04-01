package zio.test
import zio.Scope
import zio.test.ExecutionEvent.SectionEnd

object ExecutionEventPrinterSpec extends ZIOSpecDefault {
  override def spec: ZSpec[TestEnvironment with Scope, Any] =
    test("does not produce blank lines") {
      for {
        _ <- ExecutionEventPrinter.print(
               SectionEnd(
                 List("end"),
                 SuiteId(1),
                 List(SuiteId(0))
               )
             )
      } yield assertTrue(true)
    }.provide(ExecutionEventPrinter.live, TestLogger.fromConsole)
}

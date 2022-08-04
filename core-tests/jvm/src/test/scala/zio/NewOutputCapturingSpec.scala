package zio

//import zio.Console.printLine
import zio.Console.printLine
import zio.test.{FiberRefTestOutput, ZIOSpecDefault, assertCompletes}

object NewOutputCapturingSpec extends ZIOSpecDefault {
  def logic(label: String) =
    for {
//      _ <- TestOutputZ.log(FiberRefTestOutput.outputRef)(label)
      _ <- printLine(label)
      _ <- FiberRefTestOutput.outputRef.get.debug(s"$label output")
//      _ <- appender.getOutput.debug(s"Captured output $label")
    } yield ()

  def spec = suite("new output")(
    test("A"){
      logic("A") *>
        printLine("Inner output") *>
        assertCompletes
    },
//    suite("Suite B")(
//      suite("Inner B") (
//        test("B"){
//          logic("B") *>
//            assertCompletes
//        },
//      )
//    )
  )


}

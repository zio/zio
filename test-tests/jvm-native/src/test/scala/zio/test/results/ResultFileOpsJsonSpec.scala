package zio.test.results

import zio._
import zio.test._

import java.nio.file.Path
import scala.util.Using

object ResultFileOpsJsonSpec extends ZIOSpecDefault {
  def spec = suite("ResultFileOpsJsonSpec")(
    test("simple write")(
      for {
        _ <- writeToTestFile("a")
        results <- readFile
      } yield assertTrue(results == List("a"))
    ).provide(ResultFileOpsJson.test),
    test("clobbered concurrent writes") {
      val linesToWrite =
        List(
          "a",
          "b",
          "c",
          "d",
          "e"
        ).map(_ * 100)
      for {
        _ <-
          ZIO.foreachPar(
            linesToWrite
          )(writeToTestFile)
        results <- readFile
      } yield assertTrue(linesToWrite.forall(results.contains))
    }
      .provide(ResultFileOpsJson.test)
      @@ TestAspect.nonFlaky,
    test("generated concurrent writes clean") {
      checkN(10)(Gen.listOfN(3)(Gen.alphaNumericStringBounded(0, 700))) { linesToWrite =>
        for {
          _ <-
              ZIO.foreachPar(
                linesToWrite
              )(writeToTestFile)
          results <- readFile
        } yield assertTrue(linesToWrite.forall(results.contains))
      }
    }.provide(ResultFileOpsJson.test)
  )

  private def writeToTestFile(content: String) =
    ZIO.serviceWithZIO[ResultFileOpsJson](_.write(content + "\n", append = true))

  val readFile: ZIO[Path, Nothing, List[String]] =
    for {
      tmpFilePath <- ZIO.service[Path]
      lines <- ZIO.attempt {
                 Using(scala.io.Source.fromFile(tmpFilePath.toString)) { source =>
                   source.getLines().toList
                 }.get
               }.orDie
    } yield lines

}

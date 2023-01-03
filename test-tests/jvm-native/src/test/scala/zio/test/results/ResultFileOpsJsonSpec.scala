package zio.test.results

import zio._
import zio.test._

import java.nio.file.Path

object ResultFileOpsJsonSpec extends ZIOSpecDefault {
  def spec = suite("ResultFileOpsJsonSpec")(
    test("simple write")(
      for {
        _ <-
          ZIO.serviceWithZIO[ResultFileOpsJson](_.write("a", append = false))
        results <- readFile
      } yield assertTrue(results == List("a"))
    )
      .provide(ResultFileOpsJson.test),
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
          ZIO.serviceWithZIO[ResultFileOpsJson] { instance =>
            ZIO.foreachPar(
              linesToWrite
            )(x => instance.write(x + "\n", append = true))
          }
        results <- readFile
      } yield assertTrue(linesToWrite.forall(results.contains(_)))
    }
      .provide(ResultFileOpsJson.test)
      @@ TestAspect.nonFlaky,
    test("generated concurrent writes clean") {
      checkN(10)(Gen.listOfN(3)(Gen.alphaNumericStringBounded(0, 700))) { linesToWrite =>
        for {
          _ <-
            ZIO.serviceWithZIO[ResultFileOpsJson] { instance =>
              ZIO.foreachPar(
                linesToWrite
              )(x => instance.write(x + "\n", append = true))
            }
          results <- readFile
        } yield assertTrue(linesToWrite.forall(results.contains(_)))
      }
    }.provide(ResultFileOpsJson.test)
  )

  val readFile: ZIO[Path, Nothing, List[String]] = {
    for {
      tmpFilePath <- ZIO.service[Path]
      lines <- ZIO.attempt {
                 import scala.io.Source
                 Source.fromFile(tmpFilePath.toString()).getLines().toList
               }.orDie
    } yield lines
  }
}

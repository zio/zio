package zio.test.results

import zio._
import zio.test._

import java.nio.file.Path

object ResultFileOpsJsonSpec extends ZIOBaseSpec {
  def spec = suite("ResultFileOpsJsonSpec")(
    test("trailing comma from last entry is removed")(
      for {
        path    <- writeToTestFile(parallel = false)("\naaa", "\nbbb", "\nccc")
        results <- readFile(path)
      } yield assertTrue(
        results(0) == "{",
        results(1) == "  \"results\": [",
        results(2) == "aaa",
        results(3) == "bbb",
        results(4) == "ccc",
        results(5) == "  ]",
        results(6) == "}"
      )
    ),
    test("trailing comma from last entry is removed")(
      for {
        path    <- writeToTestFile(parallel = false)("\naaa,", "\nbbb,", "\nccc,")
        results <- readFile(path)
      } yield assertTrue(
        results(0) == "{",
        results(1) == "  \"results\": [",
        results(2) == "aaa,",
        results(3) == "bbb,",
        results(4) == "ccc",
        results(5) == "  ]",
        results(6) == "}"
      )
    ),
    test("is thread safe") {
      checkN(10)(Gen.setOfN(100)(Gen.alphaNumericStringBounded(1, 10))) { linesToWrite =>
        for {
          path    <- writeToTestFile(parallel = true)(linesToWrite.toList.map("\n" + _): _*)
          results <- readFile(path).map(_.toSet)
          union    = linesToWrite.intersect(results)
        } yield assertTrue(union == linesToWrite)
      }
    }
  )

  private def writeToTestFile(parallel: Boolean)(content: String*): Task[Path] =
    ZIO.scoped(for {
      path   <- ZIO.attemptBlocking(java.nio.file.Files.createTempFile("zio-test", ".json"))
      writer <- ResultFileOps.Json(path.toString)
      _ <-
        if (parallel) ZIO.foreachParDiscard(content)(writer.write(_))
        else ZIO.foreachDiscard(content)(writer.write(_))
    } yield path)

  def readFile(path: Path): Task[Vector[String]] =
    ZIO.acquireReleaseWith(ZIO.succeed(scala.io.Source.fromFile(path.toString)))(s => ZIO.succeed(s.close)) { s =>
      ZIO.succeed(s.getLines().toVector)
    }
}

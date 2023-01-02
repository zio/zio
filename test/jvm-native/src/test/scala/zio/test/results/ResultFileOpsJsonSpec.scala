package zio.test.results

import zio.ZIO

import java.nio.file.Path

object ResultFileOpsJsonSpec extends ZIOSpecDefault {
  // TODO Can we provide an alternative of suite that defaults to using the class name?
  def spec = suite("ResultFileOpsJsonSpec")(
    suite("a")(
      suite("a1")(
        test("b")(assertCompletes)
      )
    ),
    suite("a")(
      test("a1/b")(assertCompletes)
    ),
    test("simple write") {
      for {
        _ <-
          ZIO.serviceWithZIO[ResultFileOpsJson](_.write("a", append = true))
      } yield assertCompletes
    }
      .provide(ResultFileOpsJson.test)
      @@ TestAspect.ignore,
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
      @@ TestAspect.nonFlaky
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

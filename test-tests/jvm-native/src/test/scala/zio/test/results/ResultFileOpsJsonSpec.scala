package zio.test.results

import zio._
import zio.test._

import java.nio.file.Path

object ResultFileOpsJsonSpec extends ZIOBaseSpec {
  def spec = suite("ResultFileOpsJsonSpec")(
    test("simple write")(
      for {
        _       <- writeToTestFile("a")
        results <- readFile
      } yield assertTrue(results == List("a"))
    ).provide(test, Scope.default),
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
      } yield assertTrue(linesToWrite.forall(results.contains(_)))
    }
      .provide(test, Scope.default)
      @@ TestAspect.nonFlaky,
    test("generated concurrent writes clean") {
      checkN(10)(Gen.listOfN(3)(Gen.alphaNumericStringBounded(0, 700))) { linesToWrite =>
        for {
          _ <-
            ZIO.foreachPar(
              linesToWrite
            )(writeToTestFile)
          results <- readFile
        } yield assertTrue(linesToWrite.forall(results.contains(_)))
      }
    }.provide(test, Scope.default)
  )

  private def writeToTestFile(content: String) =
    ZIO.serviceWithZIO[ResultFileOps](_.write(content + "\n", append = true))

  val readFile: ZIO[Path with Scope, Nothing, List[String]] =
    for {
      tmpFilePath <- ZIO.service[Path]
      source       = scala.io.Source.fromFile(tmpFilePath.toString)
      _           <- ZIO.addFinalizer(ZIO.succeed(source.close()))
      lines <- ZIO.attempt {
                 source.getLines().toList
               }.orDie
    } yield lines

  val test: ZLayer[Any, Throwable, Path with ResultFileOps.Json] =
    ZLayer.fromZIO {
      for {
        fileLock <- Ref.Synchronized.make[Unit](())
        result <- ZIO
                    .attempt(
                      java.nio.file.Files.createTempFile("zio-test", ".json")
                    )
                    .map(path => (path, ResultFileOps.Json(path.toString, fileLock)))
      } yield result
    }.flatMap(tup => ZLayer.succeed(tup.get._1) ++ ZLayer.succeed(tup.get._2))
}

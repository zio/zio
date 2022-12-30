package zio.test.results

import zio._
import zio.test._

import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala

object ResultFileOpsJsonSpec extends ZIOSpecDefault {
  // TODO Can we provide an alternative of suite that defaults to using the class name?
  def spec = suite("ResultFileOpsJsonSpec")(
    test("simple write") {
      for {
        _ <-
          ZIO.serviceWithZIO[ResultFileOpsJson](_.write("a", append = true))
      } yield assertCompletes
    }
      .provide(ResultFileOpsJson.test)
    @@ TestAspect.ignore
    ,
    test("clobbered concurrent writes") {
      for {
        _ <-
          ZIO.serviceWithZIO[ResultFileOpsJson](instance =>
            ZIO.foreachPar(Range(0,10000).toList)(
              _ => instance.write("a", append = true)
            )
          )
        results <- readFile.debug

      } yield assertTrue(results.head.length == 1000)
    }
      .provide(ResultFileOpsJson.test)
    ,
    test("clobbered concurrent writes") {
      for {
        _ <-
          ZIO.serviceWithZIO[ResultFileOpsJson](instance =>
            {
              ZIO.foreachPar(
                List(
                  ZIO.foreachPar(Range(0, 10000).toList)(
                    _ => instance.write("a", append = true)
                  ),
                  ZIO.foreachPar(Range(0, 10000).toList)(
                    _ => instance.write("b", append = true)
                  )
                )
              )( x => x)
            }
          )
        results <- readFile.debug

      } yield assertTrue(results.head == "a" * 10000 || results.tail.head == "a" * 10000) &&
        assertTrue(results.head == "b" * 10000 || results.tail.head == "b" * 10000)
    }
      .provide(ResultFileOpsJson.test)
  )

  val readFile: ZIO[Path, Nothing, List[String]] = {
    for {
      tmpFilePath <- ZIO.service[Path]
      lines <- ZIO.attempt{
        import java.nio.file.{Files}
        Files.readAllLines(tmpFilePath).asScala.toList
      }.orDie.debug
//      _ <- ZIO.attemptBlockingIO(tmpFilePath.toFile.deleteOnExit())
    } yield  lines
  }
}

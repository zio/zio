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
      val repetition = 10000
      for {
        _ <-
          ZIO.serviceWithZIO[ResultFileOpsJson](instance =>
            {
              ZIO.foreachPar(
                List(
                    instance.write("a" * repetition + "\n", append = true),
                    instance.write("b" * repetition + "\n", append = true)
                  )
              )( x => x)
            }
          )
        results <- readFile.debug

      } yield assertTrue(results.head == "a" * repetition || results.tail.head == "a" * repetition) &&
        assertTrue(results.head == "b" * repetition || results.tail.head == "b" * repetition)
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

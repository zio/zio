package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import java.nio.file.Files

object ZSinkPlatformSpecificSpec extends ZIOBaseSpec {
  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = suite("ZSink JVM")(
    suite("fromFile")(
      testM("writes to an existing file") {
        val data = (0 to 100).mkString

        Task(Files.createTempFile("stream", "fromFile"))
          .bracket(path => Task(Files.delete(path)).orDie) { path =>
            for {
              bytes  <- Task(data.getBytes("UTF-8"))
              length <- ZStream.fromIterable(bytes).run(ZSink.fromFile(path))
              str    <- Task(new String(Files.readAllBytes(path)))
            } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
          }

      }
    )
  )
}

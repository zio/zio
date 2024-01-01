package zio.stream

import zio._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

object ZSinkPlatformSpecific2Spec extends ZIOBaseSpec {

  override def spec: Spec[Any, Throwable] = suite("ZSink JVM - Native")(
    suite("fromFile")(
      test("writes to an existing file") {
        val data = (0 to 100).mkString

        ZIO.acquireReleaseWith {
          ZIO.attempt(Files.createTempFile("stream", "fromFile"))
        } { path =>
          ZIO.attempt(Files.delete(path)).orDie
        } { path =>
          for {
            bytes  <- ZIO.attempt(data.getBytes(UTF_8))
            length <- ZStream.fromIterable(bytes).run(ZSink.fromPath(path))
            str    <- ZIO.attempt(new String(Files.readAllBytes(path)))
          } yield {
            assertTrue(data == str) &&
            assertTrue(bytes.length.toLong == length)
          }
        }
      }
    )
  )
}

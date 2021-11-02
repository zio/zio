package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import java.nio.file.Files
import java.io.ByteArrayOutputStream

object ZSinkPlatformSpecificSpec extends ZIOBaseOldSpec {
  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = suite("ZSink JVM")(
    suite("fromFile")(
      test("writes to an existing file") {
        val data = (0 to 100).mkString

        Task(Files.createTempFile("stream", "fromFile"))
          .acquireReleaseWith(path => Task(Files.delete(path)).orDie) { path =>
            for {
              bytes  <- Task(data.getBytes("UTF-8"))
              length <- ZStream.fromIterable(bytes).run(ZSink.fromFile(path))
              str    <- Task(new String(Files.readAllBytes(path)))
            } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
          }

      }
    ),
    suite("fromOutputStream")(
      test("writes to byte array ouput stream") {
        val data = (0 to 100).mkString

        for {
          bytes  <- Task(data.getBytes("UTF-8"))
          os      = new ByteArrayOutputStream(data.length)
          length <- ZStream.fromIterable(bytes).run(ZSink.fromOutputStream(os))
          str    <- Task(os.toString("UTF-8"))
        } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
      }
    )
  )
}

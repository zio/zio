package zio.stream

import zio._
import zio.blocking.Blocking
import zio.test.Assertion._
import zio.test._

import java.nio.file.Files
import java.security.MessageDigest

object ZSinkPlatformSpecificSpec extends ZIOBaseSpec {
  override def spec: Spec[Blocking, TestFailure[Throwable], TestSuccess] = suite("ZSink JVM")(
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
    ),
    suite("digest")(
      testM("should calculate digest for a stream") {
        for {
          res <- ZStream
                   .fromIterable("Hello!".getBytes())
                   .run(ZSink.digest(MessageDigest.getInstance("SHA-1")))
        } yield {
          assert(res)(
            equalTo(
              Chunk[Byte](105, 52, 44, 92, 57, -27, -82, 95, 0, 119, -82, -52, 50, -64, -8, 24, 17, -5, -127, -109)
            )
          )
        }
      },
      testM("should calculate digest for an empty stream") {
        for {
          res <- ZStream.empty
                   .run(ZSink.digest(MessageDigest.getInstance("SHA-1")))
        } yield {
          assert(res)(
            equalTo(
              Chunk[Byte](-38, 57, -93, -18, 94, 107, 75, 13, 50, 85, -65, -17, -107, 96, 24, -112, -81, -40, 7, 9)
            )
          )
        }
      }
    )
  )
}

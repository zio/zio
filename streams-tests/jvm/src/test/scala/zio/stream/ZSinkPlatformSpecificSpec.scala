package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest

object ZSinkPlatformSpecificSpec extends ZIOBaseSpec {

  override def spec: ZSpec[Any, Throwable] = suite("ZSink JVM")(
    suite("fromFile")(
      test("writes to an existing file") {
        val data = (0 to 100).mkString

        ZIO
          .attempt(Files.createTempFile("stream", "fromFile"))
          .acquireReleaseWith(path => ZIO.attempt(Files.delete(path)).orDie) { path =>
            for {
              bytes  <- ZIO.attempt(data.getBytes(UTF_8))
              length <- ZStream.fromIterable(bytes).run(ZSink.fromPath(path))
              str    <- ZIO.attempt(new String(Files.readAllBytes(path)))
            } yield assertTrue(data == str) &&
            assertTrue(bytes.length.toLong == length)
          }
      }
    ),
    suite("fromOutputStream")(
      test("writes to byte array output stream") {
        val data = (0 to 100).mkString

        for {
          bytes  <- ZIO.attempt(data.getBytes("UTF-8"))
          os      = new ByteArrayOutputStream(data.length)
          length <- ZStream.fromIterable(bytes).run(ZSink.fromOutputStream(os))
          str    <- ZIO.attempt(os.toString("UTF-8"))
        } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
      }
    ),
    suite("digest")(
      test("should calculate digest for a stream") {
        for {
          res <- ZStream
                   .fromIterable("Hello!".getBytes())
                   .run(ZSink.digest(MessageDigest.getInstance("SHA-1")))
        } yield assert(res)(
          equalTo(
            Chunk[Byte](105, 52, 44, 92, 57, -27, -82, 95, 0, 119, -82, -52, 50, -64, -8, 24, 17, -5, -127, -109)
          )
        )
      },
      test("should calculate digest for an empty stream") {
        for {
          res <- ZStream.empty
                   .run(ZSink.digest(MessageDigest.getInstance("SHA-1")))
        } yield assert(res)(
          equalTo(
            Chunk[Byte](-38, 57, -93, -18, 94, 107, 75, 13, 50, 85, -65, -17, -107, 96, 24, -112, -81, -40, 7, 9)
          )
        )
      }
    )
  )
}

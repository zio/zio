package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, NoSuchFileException, Paths}

object ZStreamPlatformSpecific2Spec extends ZIOBaseSpec {

  def spec = suite("ZStream JVM - Native")(
    suite("Constructors")(
      suite("fromFile")(
        test("reads from an existing file") {
          val data = (0 to 100).mkString

          ZIO.acquireReleaseWith {
            ZIO.attempt(Files.createTempFile("stream", "fromFile"))
          } { path =>
            ZIO.attempt(Files.delete(path)).orDie
          } { path =>
            ZIO.attempt(Files.write(path, data.getBytes(StandardCharsets.UTF_8))) *>
              assertZIO(
                ZStream
                  .fromPath(path, 24)
                  .via(ZPipeline.utf8Decode)
                  .mkString
              )(equalTo(data))
          }
        },
        test("fails on a nonexistent file") {
          assertZIO(ZStream.fromPath(Paths.get("nonexistent"), 24).runDrain.exit)(
            fails(isSubtype[NoSuchFileException](anything))
          )
        }
      ),
      suite("from")(
        suite("java.util.stream.Stream")(
          test("JavaStream") {
            trait A
            trait JavaStreamLike[A] extends java.util.stream.Stream[A]
            lazy val javaStream: JavaStreamLike[A]        = ???
            lazy val actual                               = ZStream.from(javaStream)
            lazy val expected: ZStream[Any, Throwable, A] = actual
            lazy val _                                    = expected
            assertCompletes
          },
          // test("JavaStreamScoped") {
          //   trait R
          //   trait E extends Throwable
          //   trait A
          //   trait JavaStreamLike[A] extends java.util.stream.Stream[A]
          //   lazy val javaStreamScoped: ZIO[R with Scope, E, JavaStreamLike[A]] = ???
          //   lazy val actual                                               = ZStream.from(javaStreamScoped)
          //   lazy val expected: ZStream[R, Throwable, A]                   = actual
          //   lazy val _                                                    = expected
          //   assertCompletes
          // },
          test("JavaStreamZIO") {
            trait R
            trait E extends Throwable
            trait A
            trait JavaStreamLike[A] extends java.util.stream.Stream[A]
            lazy val javaStreamZIO: ZIO[R, E, JavaStreamLike[A]] = ???
            lazy val actual                                      = ZStream.from(javaStreamZIO)
            lazy val expected: ZStream[R, Throwable, A]          = actual
            lazy val _                                           = expected
            assertCompletes
          }
        )
      ),
      suite("fromReader")(
        test("reads non-empty file") {
          ZIO.acquireReleaseWith {
            ZIO.attempt(Files.createTempFile("stream", "reader"))
          } { path =>
            ZIO.succeed(Files.delete(path))
          } { path =>
            for {
              data <- ZIO.succeed((0 to 100).mkString)
              _    <- ZIO.attempt(Files.write(path, data.getBytes("UTF-8")))
              read <- ZStream.fromReader(new FileReader(path.toString)).runCollect.map(_.mkString)
            } yield assert(read)(equalTo(data))
          }
        },
        test("reads empty file") {
          ZIO.acquireReleaseWith {
            ZIO.attempt(Files.createTempFile("stream", "reader-empty"))
          } { path =>
            ZIO.succeed(Files.delete(path))
          } { path =>
            ZStream
              .fromReader(new FileReader(path.toString))
              .runCollect
              .map(_.mkString)
              .map(assert(_)(isEmptyString))
          }
        },
        test("fails on a failing reader") {
          final class FailingReader extends Reader {
            def read(x: Array[Char], a: Int, b: Int): Int = throw new IOException("failed")

            def close(): Unit = ()
          }

          ZStream
            .fromReader(new FailingReader)
            .runDrain
            .exit
            .map(assert(_)(fails(isSubtype[IOException](anything))))
        }
      )
    )
  )
}

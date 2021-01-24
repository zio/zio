package zio

import zio.test.Assertion._
import zio.test._

import java.io.{File, IOException}
import java.nio.file.Files
import java.{util => ju}

object ZManagedPlatformSpecificSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("ZManagedPlatformSpecificSpec")(
    testM("writeFile & readFile & OutputStream.write & InputStream.readAll") {
      val fixture = Chunk[Byte](1, 2, 3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _      <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged.readFile(path).use(fis => fis.readAll(4096))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(fixture))
    },
    testM("writeFile & readFile & OutputStream.write & InputStream.skip & InputStream.readAll") {
      val fixture       = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val skipped2Bytes = Chunk[Byte](3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _      <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged.readFile(path).use(fis => fis.skip(2) *> fis.readAll(4096))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(skipped2Bytes))
    },
    testM("writeFile & readFile & OutputStream.write & InputStream.readN") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](1, 2, 3, 6)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _      <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged.readFile(path).use(fis => fis.readN(4))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(read4Bytes))
    },
    testM("writeFile & readURI & OutputStream.write & InputStream.readAll") {
      val fixture = Chunk[Byte](1, 2, 3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _      <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged.readURI(path.toUri()).use(is => is.readAll(4096))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(fixture))
    },
    testM("writeFile & readURI & OutputStream.write & InputStream.readN") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](1, 2, 3, 6)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _      <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged.readURI(path.toUri()).use(is => is.readN(4))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(read4Bytes))
    },
    testM("writeFile & readURI & OutputStream.write & InputStream.skip & InputStream.readAll") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _      <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged.readURI(path.toUri()).use(is => is.skip(2) *> is.readAll(4096))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(read4Bytes))
    },
    testM("writeFile & readURL & OutputStream.write & InputStream.readAll") {
      val fixture = Chunk[Byte](1, 2, 3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _ <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged
                                        .readURL(s"file://${path.toString()}")
                                        .use(is => is.readAll(4096))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(fixture))
    },
    testM("writeFile & readURL & OutputStream.write & InputStream.readN") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](1, 2, 3, 6)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _ <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged
                                        .readURL(s"file://${path.toString()}")
                                        .use(is => is.readN(4))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(read4Bytes))
    },
    testM("writeFile & readURL & OutputStream.write & InputStream.skip & InputStream.readAll") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                        .tempFileResource()
                        .mapEffect(f => f.toPath())
                        .use { path =>
                          for {
                            _ <- ZManaged.writeFile(path).use(fos => fos.write(fixture))
                            result <- ZManaged
                                        .readURL(s"file://${path.toString()}")
                                        .use(is => is.skip(2) *> is.readAll(4096))
                          } yield result
                        }
      } yield assert(readResult)(equalTo(read4Bytes))
    }
  )

}

object ZManagedPlatformSpecificSpecHelper {
  def tempFileResource(): ZManaged[Any, IOException, File] =
    ZManaged
      .make(
        ZIO.effect(File.createTempFile(ju.UUID.randomUUID().toString(), null)).refineToOrDie[IOException]
      )(f => ZIO.effect(Files.delete(f.toPath)).orDie)
}

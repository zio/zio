package zio

import zio.test._
import java.io.IOException
import java.io.File
import java.{ util => ju }
import java.nio.file.Files
import zio.test.Assertion._

object ZManagedPlatformSpecificSpec extends ZIOBaseSpec {

  def spec = suite("ZManagedPlatformSpecificSpec")(
    testM("writeFile & readFile & FileOutputStream.write & FileInputStream.readAll") {
      val fixture = Chunk[Byte](1, 2, 3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _      <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific.readFile(path).use(fis => fis.readAll)
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(fixture)))
    },
    testM("writeFile & readFile & FileOutputStream.write & FileInputStream.skip & FileInputStream.readAll") {
      val fixture       = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val skipped2Bytes = Chunk[Byte](3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _      <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific.readFile(path).use(fis => fis.skip(2) *> fis.readAll)
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(skipped2Bytes)))
    },
    testM("writeFile & readFile & FileOutputStream.write & FileInputStream.readN") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](1, 2, 3, 6)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _      <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific.readFile(path).use(fis => fis.readN(4))
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(read4Bytes)))
    },
    testM("writeFile & readURI & FileOutputStream.write & InputStream.readAll") {
      val fixture = Chunk[Byte](1, 2, 3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _      <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific.readURI(path.toUri()).use(is => is.readAll)
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(fixture)))
    },
    testM("writeFile & readURI & FileOutputStream.write & InputStream.readN") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](1, 2, 3, 6)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _      <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific.readURI(path.toUri()).use(is => is.readN(4))
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(read4Bytes)))
    },
    testM("writeFile & readURI & FileOutputStream.write & InputStream.skip & InputStream.readAll") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _      <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific.readURI(path.toUri()).use(is => is.skip(2) *> is.readAll)
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(read4Bytes)))
    },
    testM("writeFile & readURL & FileOutputStream.write & InputStream.readAll") {
      val fixture = Chunk[Byte](1, 2, 3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _ <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific
                                      .readURL(s"file://${path.toString()}")
                                      .use(is => is.readAll)
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(fixture)))
    },
    testM("writeFile & readURL & FileOutputStream.write & InputStream.readN") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](1, 2, 3, 6)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _ <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific
                                      .readURL(s"file://${path.toString()}")
                                      .use(is => is.readN(4))
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(read4Bytes)))
    },
    testM("writeFile & readURL & FileOutputStream.write & InputStream.skip & InputStream.readAll") {
      val fixture    = Chunk[Byte](1, 2, 3, 6, 5, 4)
      val read4Bytes = Chunk[Byte](3, 6, 5, 4)
      for {
        readResult <- ZManagedPlatformSpecificSpecHelper
                       .tempFileResource()
                       .mapEffect(f => f.toPath())
                       .use { path =>
                         for {
                           _ <- ZManagedPlatformSpecific.writeFile(path).use(fos => fos.write(fixture))
                           result <- ZManagedPlatformSpecific
                                      .readURL(s"file://${path.toString()}")
                                      .use(is => is.skip(2) *> is.readAll)
                         } yield result
                       }
      } yield assert(readResult)(equalTo(Some(read4Bytes)))
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

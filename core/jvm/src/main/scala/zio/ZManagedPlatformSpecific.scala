package zio

import java.io
import java.io.IOException
import java.net.{ URI, URL }
import java.nio.file.Path

import zio.blocking.{ Blocking, _ }

private[zio] trait ZManagedPlatformSpecific { self =>

  case class FileInputStream private (private val fis: java.io.FileInputStream) {

    def readN(n: Int): ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
      effectBlocking {
        val available = fis.available()
        available match {
          case 0 => Some(Chunk.empty)
          case _ =>
            val length         = if (available <= n) available else n
            val b: Array[Byte] = new Array[Byte](length)
            fis.read(b)
            Some(Chunk.fromArray(b))
        }
      }.refineToOrDie[IOException]

    def readAll: ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
      effectBlocking(fis.available())
        .flatMap(available =>
          effectBlocking {
            available match {
              case 0 => Some(Chunk.empty)
              case _ =>
                val b: Array[Byte] = new Array[Byte](available)
                fis.read(b)
                Some(Chunk.fromArray(b))
            }
          }
        )
        .refineToOrDie[IOException]

    def skip(n: Long): ZIO[Blocking, IOException, Long] =
      effectBlocking(fis.skip(n)).refineToOrDie[IOException]

    def close(): ZIO[Blocking, Nothing, Unit] =
      effectBlocking(fis.close()).orDie

  }

  case class InputStream private (private val is: java.io.InputStream) {
    def readN(n: Int): ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
      effectBlocking {
        val available = is.available()
        available match {
          case 0 => Some(Chunk.empty)
          case _ =>
            val length         = if (available <= n) available else n
            val b: Array[Byte] = new Array[Byte](length)
            is.read(b)
            Some(Chunk.fromArray(b))
        }
      }.refineToOrDie[IOException]

    def readAll: ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
      effectBlocking(is.available())
        .flatMap(available =>
          effectBlocking {
            available match {
              case 0 => Some(Chunk.empty)
              case _ =>
                val b: Array[Byte] = new Array[Byte](available)
                is.read(b)
                Some(Chunk.fromArray(b))
            }
          }
        )
        .refineToOrDie[IOException]

    def close(): ZIO[Blocking, Nothing, Unit] =
      effectBlocking(is.close()).orDie
  }

  case class FileOutputStream private (private val os: java.io.OutputStream) {
    def write(chunk: Chunk[Byte]): ZIO[Blocking, IOException, Unit] =
      effectBlocking {
        os.write(chunk.toArray)
        os.flush()
      }.refineToOrDie[IOException]

    def close(): ZIO[Blocking, Nothing, Unit] =
      effectBlocking(os.close()).orDie
  }

  def readFile(path: Path): ZManaged[Blocking, IOException, FileInputStream] =
    ZManaged.make(
      effectBlocking(FileInputStream(new io.FileInputStream(path.toFile)))
        .refineToOrDie[IOException]
    )(
      _.close()
    )

  def readFile(path: String): ZManaged[Blocking, IOException, FileInputStream] =
    ZManaged.make(
      effectBlocking(FileInputStream(new io.FileInputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def readFileToInputStream(path: String): ZManaged[Blocking, IOException, InputStream] =
    ZManaged.make(
      effectBlocking(InputStream(new io.FileInputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def readURL(url: URL): ZManaged[Blocking, IOException, InputStream] =
    ZManaged.make(
      effectBlocking(InputStream(url.openStream()))
        .refineToOrDie[IOException]
    )(_.close())

  def readURL(url: String): ZManaged[Blocking, IOException, InputStream] =
    ZManaged.make(
      effectBlocking(InputStream(new URL(url).openStream()))
        .refineToOrDie[IOException]
    )(_.close())

  def readURI(uri: URI): ZManaged[Blocking, IOException, InputStream] =
    for {
      isAbsolute <- ZManaged.fromEffect(effectBlocking(uri.isAbsolute()).refineToOrDie[IOException])
      is         <- if (isAbsolute) readURL(uri.toURL()) else readFileToInputStream(uri.toString())
    } yield is

  def writeFile(path: String): ZManaged[Blocking, IOException, FileOutputStream] =
    ZManaged.make(
      effectBlocking(FileOutputStream(new io.FileOutputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def writeFile(path: Path): ZManaged[Blocking, IOException, FileOutputStream] =
    writeFile(path.toString())

}

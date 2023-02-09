package zio.stream.compression

import zio._
import zio.stream._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.zip.{
  CRC32,
  Deflater,
  DeflaterInputStream,
  GZIPInputStream,
  GZIPOutputStream,
  Inflater,
  InflaterInputStream
}
import scala.annotation.tailrec

object TestData {

  val inflateRandomExampleThatFailed: Array[Byte] =
    Array(100, 96, 2, 14, 108, -122, 110, -37, 35, -11, -10, 14, 47, 30, 43, 111, -80, 44, -34, 35, 35, 37, -103).map(
      _.toByte
    )

  def deflatedStream(bytes: Array[Byte]): ZStream[Any, Nothing, Byte] =
    deflatedWith(bytes, new Deflater())

  def noWrapDeflatedStream(bytes: Array[Byte]): ZStream[Any, Nothing, Byte] =
    deflatedWith(bytes, new Deflater(9, true))

  def jdkDeflate(bytes: Array[Byte], deflater: Deflater): Array[Byte] = {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val dif       = new DeflaterInputStream(new ByteArrayInputStream(bytes), deflater)
    val read      = dif.read(bigBuffer, 0, bigBuffer.length)
    Arrays.copyOf(bigBuffer, read)
  }

  def deflatedWith(bytes: Array[Byte], deflater: Deflater): ZStream[Any, Nothing, Byte] = {
    val arr = jdkDeflate(bytes, deflater)
    ZStream.fromIterable(arr)
  }

  def jdkInflate(bytes: Chunk[Byte], noWrap: Boolean): UIO[List[Byte]] = ZIO.succeed {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val inflater  = new Inflater(noWrap)
    val iif       = new InflaterInputStream(new ByteArrayInputStream(bytes.toArray), inflater)

    @tailrec
    def inflate(acc: List[Byte]): List[Byte] = {
      val read = iif.read(bigBuffer, 0, bigBuffer.length)
      if (read <= 0) acc
      else inflate(acc ++ bigBuffer.take(read).toList)
    }

    inflate(Nil)
  }

  def jdkGzippedStream(bytes: Array[Byte], syncFlush: Boolean = true): ZStream[Any, Nothing, Byte] =
    ZStream.fromIterable(jdkGzip(bytes, syncFlush))

  def jdkGzip(bytes: Array[Byte], syncFlush: Boolean = true): Array[Byte] = {
    val baos = new ByteArrayOutputStream(1024)
    val gzos = new GZIPOutputStream(baos, 1024, syncFlush)
    gzos.write(bytes)
    gzos.finish()
    gzos.flush()
    baos.toByteArray()
  }

  def jdkGunzip(gzipped: Chunk[Byte]): Task[List[Byte]] = ZIO.attempt {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val bais      = new ByteArrayInputStream(gzipped.toArray)
    val gzis      = new GZIPInputStream(bais)

    @tailrec
    def gunzip(acc: List[Byte]): List[Byte] = {
      val read = gzis.read(bigBuffer, 0, bigBuffer.length)
      if (read <= 0) acc
      else gunzip(acc ++ bigBuffer.take(read).toList)
    }
    gunzip(Nil)
  }

  val shortText: Array[Byte]          = "abcdefg1234567890".getBytes
  val otherShortText: Array[Byte]     = "AXXX\u0000XXXA".getBytes
  val longText: Array[Byte]           = Array.fill(1000)(shortText).flatten
  val size                            = 128
  val headerHeadBytes: Array[Byte]    = Array(31.toByte, 139.toByte, 8.toByte)
  val mTimeXflAndOsBytes: Array[Byte] = Array.fill(6)(0.toByte)

  def makeStreamWithCustomHeader(flag: Int, headerTail: Array[Byte]): ZStream[Any, Nothing, Byte] = {
    val headerHead = Array(31, 139, 8, flag, 0, 0, 0, 0, 0, 0).map(_.toByte)
    ZStream.fromIterable(headerHead ++ headerTail ++ jdkGzip(shortText).drop(10))
  }

  val headerWithExtra: ZStream[Any, Nothing, Byte] =
    makeStreamWithCustomHeader(4, (Seq(13.toByte, 0.toByte) ++ Seq.fill(13)(42.toByte)).toArray)

  val headerWithComment: ZStream[Any, Nothing, Byte] =
    makeStreamWithCustomHeader(16, "ZIO rocks!".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte))

  val headerWithFileName: ZStream[Any, Nothing, Byte] =
    makeStreamWithCustomHeader(8, "some-file-name.md".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte))

  val headerWithCrc: ZStream[Any, Nothing, Byte] = {
    val crcFlag     = 2
    val headerBytes = Array(31, 139, 8, crcFlag, 0, 0, 0, 0, 0, 0).map(_.toByte)
    val crc32       = new CRC32
    crc32.update(headerBytes)
    val crc16      = (crc32.getValue() & 0xffffL).toInt
    val crc16Byte1 = (crc16 & 0xff).toByte
    val crc16Byte2 = (crc16 >> 8).toByte
    val header     = headerBytes ++ Array(crc16Byte1, crc16Byte2)
    ZStream.fromIterable(header ++ jdkGzip(shortText).drop(10))
  }

  val headerWithAll: ZStream[Any, Nothing, Byte] = {
    val flags         = 2 + 4 + 8 + 16
    val fixedHeader   = Array(31, 139, 8, flags, 0, 0, 0, 0, 0, 0).map(_.toByte)
    val extra         = (Seq(7.toByte, 0.toByte) ++ Seq.fill(7)(99.toByte)).toArray
    val fileName      = "win32.ini".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte)
    val comment       = "the last test".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte)
    val headerUpToCrc = fixedHeader ++ extra ++ fileName ++ comment
    val crc32         = new CRC32
    crc32.update(headerUpToCrc)
    val crc16      = (crc32.getValue() & 0xffffL).toInt
    val crc16Byte1 = (crc16 & 0xff).toByte
    val crc16Byte2 = (crc16 >> 8).toByte
    val header     = headerUpToCrc ++ Array(crc16Byte1, crc16Byte2)
    ZStream.fromIterable(header ++ jdkGzip(shortText).drop(10))
  }

  def u8(b: Byte): Int = b & 0xff
}

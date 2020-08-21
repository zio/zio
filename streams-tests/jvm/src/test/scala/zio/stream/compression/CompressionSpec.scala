package zio.stream.compression

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
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
import TestData._
import zio._
import zio.stream.ZTransducer.{ deflate, gunzip, gzip, inflate }
import zio.stream._
import zio.test.Assertion._
import zio.test._

object CompressionSpec extends DefaultRunnableSpec {
  override def spec =
    suite("CompressionSpec")(
      suite("inflate")(
        testM("short stream")(
          assertM(
            deflatedStream(shortText).transduce(inflate(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("stream of two deflated inputs")(
          assertM(
            (deflatedStream(shortText) ++ deflatedStream(otherShortText)).transduce(inflate(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText) ++ Chunk.fromArray(otherShortText)))
        ),
        testM("stream of two deflated inputs as a single chunk")(
          assertM(
            (deflatedStream(shortText) ++ deflatedStream(otherShortText)).chunkN(500).transduce(inflate(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText) ++ Chunk.fromArray(otherShortText)))
        ),
        testM("long input")(
          assertM(
            deflatedStream(longText).transduce(inflate(64)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, buffer smaller than chunks")(
          assertM(
            deflatedStream(longText).chunkN(500).transduce(inflate(1)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, chunks smaller then buffer")(
          assertM(
            deflatedStream(longText).chunkN(1).transduce(inflate(500)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, not wrapped in ZLIB header and trailer")(
          assertM(
            noWrapDeflatedStream(longText).transduce(inflate(1024, true)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("fail eartly if header is corrupted")(
          assertM(
            Stream.fromIterable(Seq(1, 2, 3, 4, 5).map(_.toByte)).transduce(inflate()).runCollect.run
          )(fails(anything))
        ),
        testM("inflate what JDK deflated")(
          checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
            case ((chunk, n), bufferSize) =>
              assertM(for {
                deflated <- ZIO.effectTotal(deflatedStream(chunk.toArray))
                out      <- deflated.chunkN(n).transduce(inflate(bufferSize)).runCollect
              } yield out.toList)(equalTo(chunk))
          }
        ),
        testM("inflate what JDK deflated, nowrap")(
          checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
            case ((chunk, n), bufferSize) =>
              assertM(for {
                deflated <- ZIO.effectTotal(noWrapDeflatedStream(chunk.toArray))
                out      <- deflated.chunkN(n).transduce(inflate(bufferSize, true)).runCollect
              } yield out.toList)(equalTo(chunk))
          }
        ),
        testM("inflate nowrap: remaining = 0 but not all was pulled")(
          // This case shown error when not all data was pulled out of inflater
          assertM(for {
            input    <- ZIO.effectTotal(inflateRandomExampleThatFailed)
            deflated <- ZIO.effectTotal(noWrapDeflatedStream(input))
            out      <- deflated.chunkN(40).transduce(inflate(11, true)).runCollect
          } yield out.toList)(equalTo(inflateRandomExampleThatFailed.toList))
        ),
        testM("fail if input stream finished unexpected")(
          assertM(
            jdkGzippedStream(longText).take(800).transduce(inflate()).runCollect.run
          )(fails(anything))
        )
      ),
      suite("gunzip")(
        testM("short stream")(
          assertM(
            jdkGzippedStream(shortText).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("stream of two gzipped inputs as a single chunk")(
          assertM(
            (jdkGzippedStream(shortText) ++ jdkGzippedStream(otherShortText))
              .chunkN(500)
              .transduce(gunzip(64))
              .runCollect
          )(equalTo(Chunk.fromArray(shortText) ++ Chunk.fromArray(otherShortText)))
        ),
        testM("long input")(
          assertM(
            jdkGzippedStream(longText).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, no SYNC_FLUSH")(
          assertM(
            jdkGzippedStream(longText, false).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, buffer smaller than chunks")(
          assertM(
            jdkGzippedStream(longText).chunkN(500).transduce(gunzip(1)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, chunks smaller then buffer")(
          assertM(
            jdkGzippedStream(longText).chunkN(1).transduce(gunzip(500)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("fail early if header is corrupted")(
          assertM(
            Stream.fromIterable(1 to 10).map(_.toByte).transduce(gunzip()).runCollect.run
          )(fails(anything))
        ),
        testM("fail if input stream finished unexpected")(
          assertM(
            jdkGzippedStream(longText).take(80).transduce(gunzip()).runCollect.run
          )(fails(anything))
        ),
        testM("no output on very incomplete stream is not OK")(
          assertM(
            Stream.fromIterable(1 to 5).map(_.toByte).transduce(gunzip()).runCollect.run
          )(fails(anything))
        ),
        testM("gunzip what JDK gzipped, nowrap")(
          checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
            case ((chunk, n), bufferSize) =>
              assertM(for {
                deflated <- ZIO.effectTotal(jdkGzippedStream(chunk.toArray))
                out      <- deflated.chunkN(n).transduce(gunzip(bufferSize)).runCollect
              } yield out.toList)(equalTo(chunk))
          }
        ),
        testM("parses header with FEXTRA")(
          assertM(
            headerWithExtra.transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("parses header with FCOMMENT")(
          assertM(
            headerWithComment.transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("parses header with FNAME")(
          assertM(
            headerWithFileName.transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("parses header with CRC16")(
          assertM(
            headerWithCrc.chunkN(1).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("parses header with CRC16, FNAME, FCOMMENT, FEXTRA")(
          assertM(
            headerWithAll.transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        )
      ),
      suite("deflate")(
        testM("JDK inflates what was deflated")(
          checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
            case ((input, n), bufferSize) =>
              assertM(for {
                deflated <- Stream.fromIterable(input).chunkN(n).transduce(deflate(bufferSize, false)).runCollect
                inflated <- jdkInflate(deflated, noWrap = false)
              } yield inflated)(equalTo(input))
          }
        ),
        testM("deflate empty bytes, small buffer")(
          assertM(
            Stream.fromIterable(List.empty).chunkN(1).transduce(deflate(100, false)).runCollect.map(_.toList)
          )(equalTo(jdkDeflate(Array.empty, new Deflater(-1, false)).toList))
        ),
        testM("deflates same as JDK")(
          assertM(Stream.fromIterable(longText).chunkN(128).transduce(deflate(256, false)).runCollect)(
            equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, false))))
          )
        ),
        testM("deflates same as JDK, nowrap")(
          assertM(Stream.fromIterable(longText).chunkN(128).transduce(deflate(256, true)).runCollect)(
            equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, true))))
          )
        ),
        testM("deflates same as JDK, small buffer")(
          assertM(Stream.fromIterable(longText).chunkN(64).transduce(deflate(1, false)).runCollect)(
            equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, false))))
          )
        ),
        testM("deflates same as JDK, nowrap, small buffer ")(
          assertM(Stream.fromIterable(longText).chunkN(64).transduce(deflate(1, true)).runCollect)(
            equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, true))))
          )
        )
      ),
      suite("gzip")(
        testM("JDK gunzips what was gzipped")(
          checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
            case ((input, n), bufferSize) =>
              assertM(for {
                gzipped  <- Stream.fromIterable(input).chunkN(n).transduce(gzip(bufferSize)).runCollect
                inflated <- jdkGunzip(gzipped)
              } yield inflated)(equalTo(input))
          }
        ),
        testM("gzip empty bytes, small buffer")(
          assertM(for {
            gzipped      <- Stream.empty.transduce(gzip(1)).runCollect
            jdkGunzipped <- jdkGunzip(gzipped)
          } yield jdkGunzipped)(isEmpty)
        ),
        testM("gzip empty bytes")(
          assertM(for {
            gzipped      <- Stream.empty.transduce(gzip(`1K`)).runCollect
            jdkGunzipped <- jdkGunzip(gzipped)
          } yield jdkGunzipped)(isEmpty)
        ),
        testM("gzips, small chunks, small buffer")(
          assertM(for {
            gzipped      <- Stream.fromIterable(longText).chunkN(1).transduce(gzip(1)).runCollect
            jdkGunzipped <- jdkGunzip(gzipped)
          } yield jdkGunzipped)(equalTo(longText.toList))
        ),
        testM("gzips, small chunks, 1k buffer")(
          assertM(for {
            gzipped      <- Stream.fromIterable(longText).chunkN(1).transduce(gzip(`1K`)).runCollect
            jdkGunzipped <- jdkGunzip(gzipped)
          } yield jdkGunzipped)(equalTo(longText.toList))
        ),
        testM("chunks bigger than buffer")(
          assertM(for {
            gzipped      <- Stream.fromIterable(longText).chunkN(`1K`).transduce(gzip(64)).runCollect
            jdkGunzipped <- jdkGunzip(gzipped)
          } yield jdkGunzipped)(equalTo(longText.toList))
        ),
        testM("transducer is re-usable")(
          assertM(for {
            gzipper       <- ZIO.effectTotal(gzip(64))
            gzipped1      <- Stream.fromIterable(longText).transduce(gzipper).runCollect
            gzipped2      <- Stream.fromIterable(longText.reverse).transduce(gzipper).runCollect
            jdkGunzipped1 <- jdkGunzip(gzipped1)
            jdkGunzipped2 <- jdkGunzip(gzipped2)
          } yield jdkGunzipped1 ++ jdkGunzipped2)(equalTo(longText.toList ++ longText.reverse.toList))
        )
      )
    )

}

object TestData {

  val inflateRandomExampleThatFailed =
    Array(100, 96, 2, 14, 108, -122, 110, -37, 35, -11, -10, 14, 47, 30, 43, 111, -80, 44, -34, 35, 35, 37, -103).map(
      _.toByte
    )

  def deflatedStream(bytes: Array[Byte]) =
    deflatedWith(bytes, new Deflater())

  def noWrapDeflatedStream(bytes: Array[Byte]) =
    deflatedWith(bytes, new Deflater(9, true))

  def jdkDeflate(bytes: Array[Byte], deflater: Deflater): Array[Byte] = {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val dif       = new DeflaterInputStream(new ByteArrayInputStream(bytes), deflater)
    val read      = dif.read(bigBuffer, 0, bigBuffer.length)
    Arrays.copyOf(bigBuffer, read)
  }

  def deflatedWith(bytes: Array[Byte], deflater: Deflater) = {
    val arr = jdkDeflate(bytes, deflater)
    ZStream.fromIterable(arr)
  }

  def jdkInflate(bytes: Chunk[Byte], noWrap: Boolean): UIO[List[Byte]] = ZIO.effectTotal {
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

  def jdkGzippedStream(bytes: Array[Byte], syncFlush: Boolean = true) =
    ZStream.fromIterable(jdkGzip(bytes, syncFlush))

  def jdkGzip(bytes: Array[Byte], syncFlush: Boolean = true): Array[Byte] = {
    val baos = new ByteArrayOutputStream(1024)
    val gzos = new GZIPOutputStream(baos, 1024, syncFlush)
    gzos.write(bytes)
    gzos.finish()
    gzos.flush()
    baos.toByteArray()
  }

  def jdkGunzip(gzipped: Chunk[Byte]): Task[List[Byte]] = ZIO.effect {
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

  val shortText          = "abcdefg1234567890".getBytes
  val otherShortText     = "AXXX\u0000XXXA".getBytes
  val longText           = Array.fill(1000)(shortText).flatten
  val `1K`               = 1024
  val headerHeadBytes    = Array(31.toByte, 139.toByte, 8.toByte)
  val mTimeXflAndOsBytes = Array.fill(6)(0.toByte)

  def makeStreamWithCustomHeader(flag: Int, headerTail: Array[Byte]) = {
    val headerHead = Array(31, 139, 8, flag, 0, 0, 0, 0, 0, 0).map(_.toByte)
    ZStream.fromIterable(headerHead ++ headerTail ++ jdkGzip(shortText).drop(10))
  }

  val headerWithExtra =
    makeStreamWithCustomHeader(4, (Seq(13.toByte, 0.toByte) ++ Seq.fill(13)(42.toByte)).toArray)

  val headerWithComment =
    makeStreamWithCustomHeader(16, "ZIO rocks!".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte))

  val headerWithFileName =
    makeStreamWithCustomHeader(8, "some-file-name.md".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte))

  val headerWithCrc = {
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

  val headerWithAll = {
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

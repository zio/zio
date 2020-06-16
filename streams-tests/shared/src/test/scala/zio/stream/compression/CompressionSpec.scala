package zio.stream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.GZIPOutputStream

import TestData._

import zio.Chunk
import zio.stream.compression.{ gunzip, inflate }
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
            noWrapDeflatedStream(longText).transduce(inflate(64, true)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("fail eartly if header is corrupted")(
          assertM(
            Stream.fromIterable(Seq(1, 2, 3, 4, 5).map(_.toByte)).transduce(inflate()).runCollect.run
          )(fails(anything))
        )
      ),
      suite("gunzip")(
        testM("short stream")(
          assertM(
            gzippedStream(shortText).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText)))
        ),
        testM("stream of two gzipped inputs")(
          assertM(
            (gzippedStream(shortText) ++ gzippedStream(otherShortText)).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(shortText) ++ Chunk.fromArray(otherShortText)))
        ),
        testM("long input")(
          assertM(
            gzippedStream(longText).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, no SYNC_FLUSH")(
          assertM(
            gzippedStream(longText, false).transduce(gunzip(64)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, buffer smaller than chunks")(
          assertM(
            gzippedStream(longText).chunkN(500).transduce(gunzip(1)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("long input, chunks smaller then buffer")(
          assertM(
            gzippedStream(longText).chunkN(1).transduce(gunzip(500)).runCollect
          )(equalTo(Chunk.fromArray(longText)))
        ),
        testM("fail early if header is corrupted")(
          assertM(
            Stream.fromIterable(1 to 10).map(_.toByte).transduce(gunzip()).runCollect.run
          )(fails(anything))
        ),
        testM("no output on very incomplete stream is OK")(
          assertM(
            Stream.fromIterable(1 to 5).map(_.toByte).transduce(gunzip()).runCollect
          )(isEmpty)
        )
      )
    )

}

object TestData {

  def deflatedStream(bytes: Array[Byte]) =
    ZStream.fromIterable(new DeflaterInputStream(new ByteArrayInputStream(bytes)).readAllBytes())

  def noWrapDeflatedStream(bytes: Array[Byte]) =
    ZStream.fromIterable(new DeflaterInputStream(new ByteArrayInputStream(bytes), new Deflater(9, true)).readAllBytes())

  def gzippedStream(bytes: Array[Byte], syncFlush: Boolean = true) = {
    val baos = new ByteArrayOutputStream(1024)
    val gzos = new GZIPOutputStream(baos, 1024, syncFlush)
    gzos.write(bytes)
    gzos.finish()
    gzos.flush()
    ZStream.fromIterable(baos.toByteArray())
  }

  val shortText      = "abcdefg1234567890".getBytes
  val otherShortText = "AXXX\u0000XXXA".getBytes
  val longText       = Array.fill(1000)(shortText).flatten

}

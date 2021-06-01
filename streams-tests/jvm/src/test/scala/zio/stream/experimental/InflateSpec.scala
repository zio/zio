package zio.stream.experimental

import zio._
import zio.stream.compression.TestData._
import zio.test.Assertion._
import zio.test._

import java.util.zip.Deflater

import Inflate._

object InflateSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[Environment, Failure] =
    suite("CompressionSpec")(
      testM("short stream")(
        assertM(
          (deflatedStream(shortText).channel >>> makeInflater(64)).runCollect.map(_._1.flatten)
        )(equalTo(Chunk.fromArray(shortText)))
      ),
      testM("stream of two deflated inputs")(
        assertM(
          ((deflatedStream(shortText) ++ deflatedStream(otherShortText)).channel >>> makeInflater(64)).runCollect
            .map(_._1.flatten)
        )(equalTo(Chunk.fromArray(shortText) ++ Chunk.fromArray(otherShortText)))
      ),
      testM("stream of two deflated inputs as a single chunk")(
        assertM(
          ((deflatedStream(shortText) ++ deflatedStream(otherShortText)).chunkN(500).channel >>> makeInflater(
            64
          )).runCollect.map(_._1.flatten)
        )(equalTo(Chunk.fromArray(shortText) ++ Chunk.fromArray(otherShortText)))
      ),
      testM("long input")(
        assertM(
          (deflatedStream(longText).channel >>> makeInflater(64)).runCollect.map(_._1.flatten)
        )(equalTo(Chunk.fromArray(longText)))
      ),
      testM("long input, buffer smaller than chunks")(
        assertM(
          (deflatedStream(longText).chunkN(500).channel >>> makeInflater(1)).runCollect.map(_._1.flatten)
        )(equalTo(Chunk.fromArray(longText)))
      ),
      testM("long input, chunks smaller then buffer")(
        assertM(
          (deflatedStream(longText).chunkN(1).channel >>> makeInflater(500)).runCollect.map(_._1.flatten)
        )(equalTo(Chunk.fromArray(longText)))
      ),
      testM("long input, not wrapped in ZLIB header and trailer")(
        assertM(
          (noWrapDeflatedStream(longText).channel >>> makeInflater(1024, true)).runCollect.map(_._1.flatten)
        )(equalTo(Chunk.fromArray(longText)))
      ),
      testM("fail early if header is corrupted")(
        assertM(
          (ZStream.fromIterable(Seq(1, 2, 3, 4, 5).map(_.toByte)).channel >>> makeInflater()).runCollect.run
        )(fails(anything))
      ),
      testM("inflate what JDK deflated")(
        checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
          case ((chunk, n), bufferSize) =>
            assertM(for {
              deflated <- ZIO.effectTotal(deflatedStream(chunk.toArray))
              out      <- (deflated.chunkN(n).channel >>> makeInflater(bufferSize)).runCollect.map(_._1.flatten)
            } yield out.toList)(equalTo(chunk))
        }
      ),
      testM("inflate what JDK deflated, nowrap")(
        checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
          case ((chunk, n), bufferSize) =>
            assertM(for {
              deflated <- ZIO.effectTotal(noWrapDeflatedStream(chunk.toArray))
              out      <- (deflated.chunkN(n).channel >>> makeInflater(bufferSize, true)).runCollect.map(_._1.flatten)
            } yield out.toList)(equalTo(chunk))
        }
      ),
      testM("inflate nowrap: remaining = 0 but not all was pulled")(
        // This case shown error when not all data was pulled out of inflater
        assertM(for {
          input    <- ZIO.effectTotal(inflateRandomExampleThatFailed)
          deflated <- ZIO.effectTotal(noWrapDeflatedStream(input))
          out      <- (deflated.chunkN(40).channel >>> makeInflater(11, true)).runCollect.map(_._1.flatten)
        } yield out.toList)(equalTo(inflateRandomExampleThatFailed.toList))
      ),
      testM("fail if input stream finished unexpected")(
        assertM(
          (ZStream.fromIterable(jdkGzip(longText, true)).take(800).channel >>> makeInflater()).runCollect.run
        )(fails(anything))
      )
    )

  def deflatedStream(data: Array[Byte]): ZStream[Any, Nothing, Byte] =
    ZStream.fromIterable(jdkDeflate(data, new Deflater()))

  def noWrapDeflatedStream(data: Array[Byte]): ZStream[Any, Nothing, Byte] =
    ZStream.fromIterable(jdkDeflate(data, new Deflater(9, true)))

}

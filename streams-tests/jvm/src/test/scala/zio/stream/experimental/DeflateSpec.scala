package zio.stream.experimental

import zio._
import zio.stream.compression.TestData._
import zio.test.Assertion._
import zio.test._

import java.util.zip.Deflater

object DeflateSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[Environment, Failure] =
    suite("CompressionSpec")(
      testM("JDK inflates what was deflated")(
        checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
          case ((input, n), bufferSize) =>
            assertM(for {
              (deflated, _) <-
                (ZStream.fromIterable(input).chunkN(n).channel >>> Deflate.makeDeflater(bufferSize)).runCollect
              inflated <- jdkInflate(deflated.flatten, noWrap = false)
            } yield inflated)(equalTo(input))
        }
      ),
      testM("deflate empty bytes, small buffer")(
        assertM(
          (ZStream.fromIterable(List.empty).chunkN(1).channel >>> Deflate
            .makeDeflater(100, false)).runCollect
            .map(_._1.flatten.toList)
        )(equalTo(jdkDeflate(Array.empty, new Deflater(-1, false)).toList))
      ),
      testM("deflates same as JDK")(
        assertM(
          (ZStream.fromIterable(longText).chunkN(128).channel >>> Deflate.makeDeflater(256, false)).runCollect
            .map(_._1.flatten)
        )(
          equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, false))))
        )
      ),
      testM("deflates same as JDK, nowrap")(
        assertM(
          (ZStream.fromIterable(longText).chunkN(128).channel >>> Deflate.makeDeflater(256, true)).runCollect
            .map(_._1.flatten))(
          equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, true))))
        )
      )
      ,
      testM("deflates same as JDK, small buffer")(
        assertM(
          (ZStream.fromIterable(longText).chunkN(64).channel >>> Deflate.makeDeflater(1, false)).runCollect.map(_._1.flatten))(
          equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, false))))
        )
      ),
      testM("deflates same as JDK, nowrap, small buffer ")(
        assertM((ZStream.fromIterable(longText).chunkN(64).channel >>> Deflate.makeDeflater(1, true)).runCollect.map(_._1.flatten))(
          equalTo(Chunk.fromArray(jdkDeflate(longText, new Deflater(-1, true))))
        )
      )
    )

}

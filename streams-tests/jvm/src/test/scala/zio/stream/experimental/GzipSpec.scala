package zio.stream.experimental

import zio.stream.compression.TestData._
import zio.test.Assertion._
import zio.test._

object GzipSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[Environment, Failure] =
    suite("GzipSpec")(
      testM("JDK gunzips what was gzipped")(
        checkM(Gen.listOfBounded(0, `1K`)(Gen.anyByte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
          case ((input, n), bufferSize) =>
            assertM(for {
              gzipped <- (ZStream.fromIterable(input).chunkN(n).channel >>> Gzip.makeGzipper(bufferSize)).runCollect
                           .map(_._1.flatten)
              inflated <- jdkGunzip(gzipped)
            } yield inflated)(equalTo(input))
        }
      ),
      testM("gzip empty bytes, small buffer")(
        assertM(for {
          gzipped      <- (ZStream.empty.channel >>> Gzip.makeGzipper(1)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(isEmpty)
      ),
      testM("gzip empty bytes")(
        assertM(for {
          gzipped      <- (ZStream.empty.channel >>> Gzip.makeGzipper(`1K`)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(isEmpty)
      ),
      testM("gzips, small chunks, small buffer")(
        assertM(for {
          gzipped <-
            (ZStream.fromIterable(longText).chunkN(1).channel >>> Gzip.makeGzipper(1)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(equalTo(longText.toList))
      ),
      testM("gzips, small chunks, 1k buffer")(
        assertM(for {
          gzipped <-
            (ZStream.fromIterable(longText).chunkN(1).channel >>> Gzip.makeGzipper(`1K`)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(equalTo(longText.toList))
      ),
      testM("chunks bigger than buffer")(
        assertM(for {
          gzipped <- (ZStream.fromIterable(longText).chunkN(`1K`).channel >>> Gzip.makeGzipper(64)).runCollect
                       .map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(equalTo(longText.toList))
      ),
      testM("input >= 2^32")(
        assertM(for {
          gzipped <- (ZStream.fromIterable(longText).forever.take(65536).channel >>> Gzip.makeGzipper()).runCollect
                       .map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(hasSize(equalTo(65536)))
      )
    )
}

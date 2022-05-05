package zio.stream

import zio.stream.compression.TestData._
import zio.test.Assertion._
import zio.test._

object GzipSpec extends ZIOSpecDefault {
  override def spec =
    suite("GzipSpec")(
      test("JDK gunzips what was gzipped")(
        check(Gen.listOfBounded(0, `1K`)(Gen.byte).zip(Gen.int(1, `1K`)).zip(Gen.int(1, `1K`))) {
          case (input, n, bufferSize) =>
            assertZIO(for {
              gzipped <- (ZStream.fromIterable(input).rechunk(n).channel >>> Gzip.makeGzipper(bufferSize)).runCollect
                           .map(_._1.flatten)
              inflated <- jdkGunzip(gzipped)
            } yield inflated)(equalTo(input))
        }
      ),
      test("gzip empty bytes, small buffer")(
        assertZIO(for {
          gzipped      <- (ZStream.empty.channel >>> Gzip.makeGzipper(1)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(isEmpty)
      ),
      test("gzip empty bytes")(
        assertZIO(for {
          gzipped      <- (ZStream.empty.channel >>> Gzip.makeGzipper(`1K`)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(isEmpty)
      ),
      test("gzips, small chunks, small buffer")(
        assertZIO(for {
          gzipped <-
            (ZStream.fromIterable(longText).rechunk(1).channel >>> Gzip.makeGzipper(1)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(equalTo(longText.toList))
      ),
      test("gzips, small chunks, 1k buffer")(
        assertZIO(for {
          gzipped <-
            (ZStream.fromIterable(longText).rechunk(1).channel >>> Gzip.makeGzipper(`1K`)).runCollect.map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(equalTo(longText.toList))
      ),
      test("chunks bigger than buffer")(
        assertZIO(for {
          gzipped <- (ZStream.fromIterable(longText).rechunk(`1K`).channel >>> Gzip.makeGzipper(64)).runCollect
                       .map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(equalTo(longText.toList))
      ),
      test("input >= 2^32")(
        assertZIO(for {
          gzipped <- (ZStream.fromIterable(longText).forever.take(65536).channel >>> Gzip.makeGzipper()).runCollect
                       .map(_._1.flatten)
          jdkGunzipped <- jdkGunzip(gzipped)
        } yield jdkGunzipped)(hasSize(equalTo(65536)))
      )
    )
}

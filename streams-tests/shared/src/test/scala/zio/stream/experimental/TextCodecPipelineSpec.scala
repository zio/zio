package zio.stream.experimental

import zio._
import zio.stream.internal.CharacterSet._
import zio.test.TestAspect.{ignore, jvmOnly, nondeterministic}
import zio.test._
import ZPipeline.UtfDecodingPipeline

import java.nio.charset.{Charset, StandardCharsets}

object TextCodecPipelineSpec extends ZIOBaseSpec {

  private def stringToByteChunkOf(charset: Charset, source: String): Chunk[Byte] =
    Chunk.fromArray(source.getBytes(charset))

  private def testDecoderWithRandomStringUsing(
    decodingPipeline: UtfDecodingPipeline,
    sourceCharset: Charset,
    withBom: Chunk[Byte] => Chunk[Byte] = identity
  ) =
    check(
      Gen.string.map(stringToByteChunkOf(sourceCharset, _)),
      Gen.int
    ) {
      // Enabling `rechunk(chunkSize)` makes this suite run for too long and
      // could potentially cause OOM during builds. However, running the tests with
      // `rechunk(chunkSize)` can guarantee that different chunks have no impact on
      // the functionality of decoders. You should run it at least once locally before
      // pushing your commit.
      (originalBytes, /*chunkSize*/ _) =>
        (
          ZStream
            .fromChunk(withBom(originalBytes))
//            .rechunk(chunkSize)
            @@ decodingPipeline
        ).mkString.map { decodedString =>
          assertTrue(originalBytes == stringToByteChunkOf(sourceCharset, decodedString))
        }
    }

  private def runOnlyIfSupporting(charset: String) =
    if (Charset.isSupported(charset)) jvmOnly
    else ignore

  override def spec: ZSpec[Environment, Failure] =
    suite("TextCodecPipelineSpec")(
      suite("Text Decoders")(
        suite("utfDecode")(
          test("UTF-8 with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_8, BOM.Utf8 ++ _)
          },
          test("UTF-8 without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_8)
          },
          test("UTF-16BE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_16BE, BOM.Utf16BE ++ _)
          },
          test("UTF-16LE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_16LE, BOM.Utf16LE ++ _)
          },
          test("UTF-32BE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, CharsetUtf32BE, BOM.Utf32BE ++ _)
          },
          test("UTF-32LE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, CharsetUtf32LE, BOM.Utf32LE ++ _)
          }
        ),
        suite("utf8Decode")(
          test("with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf8Decode, StandardCharsets.UTF_8, BOM.Utf8 ++ _)
          },
          test("without BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf8Decode, StandardCharsets.UTF_8)
          }
        ),
        test("utf16BEDecode") {
          testDecoderWithRandomStringUsing(ZPipeline.utf16BEDecode, StandardCharsets.UTF_16BE)
        },
        test("utf16LEDecode") {
          testDecoderWithRandomStringUsing(ZPipeline.utf16LEDecode, StandardCharsets.UTF_16LE)
        },
        suite("utf16Decode")(
          test("UTF-16 without BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16)
          },
          test("UTF-16BE without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16BE)
          },
          test("Big Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16BE, BOM.Utf16BE ++ _)
          },
          test("Little Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16LE, BOM.Utf16LE ++ _)
          }
        ),
        test("utf32BEDecode") {
          testDecoderWithRandomStringUsing(
            ZPipeline.utf32BEDecode,
            CharsetUtf32BE
          )
        } @@ runOnlyIfSupporting("UTF-32BE"),
        test("utf32LEDecode") {
          testDecoderWithRandomStringUsing(
            ZPipeline.utf32LEDecode,
            CharsetUtf32LE
          )
        } @@ runOnlyIfSupporting("UTF-32LE"),
        suite("utf32Decode")(
          test("UTF-32 without BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32)
          },
          test("UTF-32BE without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32BE)
          },
          test("Big Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32BE, BOM.Utf32BE ++ _)
          },
          test("Little Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32LE, BOM.Utf32LE ++ _)
          }
        ) @@ runOnlyIfSupporting("UTF-32")
      )
    ) @@ nondeterministic
}

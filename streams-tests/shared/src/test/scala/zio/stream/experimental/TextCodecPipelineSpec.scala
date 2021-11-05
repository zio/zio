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

  private def fixIfGeneratedBytesBeginWithBom(
    generated: Chunk[Byte],
    sourceCharset: Charset,
    specifiedBom: Chunk[Byte]
  ) =
    // If no BOM will be prepended to the generated chunk,
    // then we want to make sure the generated doesn't start with BOM;
    // if it does start with BOM, we'll remove it otherwise the assertion
    // of `originalBytes == roundTripBytes` won't match
    if (specifiedBom.isEmpty) {
      sourceCharset match {
        case StandardCharsets.UTF_8 if generated.take(3) == BOM.Utf8 =>
          generated.drop(3)
        case StandardCharsets.UTF_16BE if generated.take(2) == BOM.Utf16BE =>
          generated.drop(2)
        case StandardCharsets.UTF_16LE if generated.take(2) == BOM.Utf16LE =>
          generated.drop(2)
        case CharsetUtf32 if generated.take(4) == BOM.Utf32BE =>
          generated.drop(4)
        case CharsetUtf32BE if generated.take(4) == BOM.Utf32BE =>
          generated.drop(4)
        case CharsetUtf32LE if generated.take(4) == BOM.Utf32LE =>
          generated.drop(4)
        case _ =>
          generated
      }
    } else generated

  private def testDecoderWithRandomStringUsing(
    decodingPipeline: UtfDecodingPipeline,
    sourceCharset: Charset,
    bom: Chunk[Byte] = Chunk.empty,
    stringGenerator: Gen[Has[Random] with Has[Sized], String] = Gen.string
  ) =
    check(
      stringGenerator.map { generatedString =>
        fixIfGeneratedBytesBeginWithBom(
          stringToByteChunkOf(sourceCharset, generatedString),
          sourceCharset,
          bom
        )
      },
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
            .fromChunk(bom ++ originalBytes)
//            .rechunk(chunkSize)
            @@ decodingPipeline
        ).mkString.map { decodedString =>
          val roundTripBytes = stringToByteChunkOf(sourceCharset, decodedString)

          assertTrue(originalBytes == roundTripBytes)
        }
    }

  private def runOnlyIfSupporting(charset: String) =
    if (Charset.isSupported(charset)) jvmOnly
    else ignore

  override def spec: ZSpec[Environment, Failure] =
    suite("TextCodecPipelineSpec")(
      suite("Text Decoders")(
        test("iso_8859_1Decode") {
          testDecoderWithRandomStringUsing(
            ZPipeline.iso_8859_1Decode,
            StandardCharsets.ISO_8859_1,
            stringGenerator = Gen.iso_8859_1
          )
        },
        test("usASCIIDecode") {
          testDecoderWithRandomStringUsing(ZPipeline.usASCIIDecode, StandardCharsets.US_ASCII)
        },
        suite("utfDecode")(
          test("UTF-8 with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_8, BOM.Utf8)
          },
          test("UTF-8 without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_8)
          },
          test("UTF-16BE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_16BE, BOM.Utf16BE)
          },
          test("UTF-16LE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_16LE, BOM.Utf16LE)
          },
          test("UTF-32BE with BOM") {
            testDecoderWithRandomStringUsing(
              ZPipeline.utfDecode,
              CharsetUtf32BE,
              BOM.Utf32BE
            )
          } @@ runOnlyIfSupporting("UTF-32BE"),
          test("UTF-32LE with BOM") {
            testDecoderWithRandomStringUsing(
              ZPipeline.utfDecode,
              CharsetUtf32LE,
              BOM.Utf32LE
            )
          } @@ runOnlyIfSupporting("UTF-32LE")
        ),
        suite("utf8Decode")(
          test("with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf8Decode, StandardCharsets.UTF_8, BOM.Utf8)
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
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16BE, BOM.Utf16BE)
          },
          test("Little Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16LE, BOM.Utf16LE)
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
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32BE, BOM.Utf32BE)
          },
          test("Little Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32LE, BOM.Utf32LE)
          }
        ) @@ runOnlyIfSupporting("UTF-32")
      )
    ) @@ nondeterministic
}

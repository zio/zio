package zio.stream.experimental

import zio._
import zio.stream.internal.CharacterSet._
import zio.test.TestAspect.{ignore, jvmOnly, nondeterministic}
import zio.test._

import java.nio.charset.{Charset, StandardCharsets}

object TextCodecPipelineSpec extends ZIOBaseSpec {

  type UtfDecodingPipeline = ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ]

  type UtfEncodingPipeline = ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ]

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
    if (specifiedBom.isEmpty)
      sourceCharset match {
        case StandardCharsets.UTF_8 if generated.take(3) == BOM.Utf8 =>
          generated.drop(3)
        case StandardCharsets.UTF_16BE if generated.take(2) == BOM.Utf16BE =>
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
    else generated

  private def testDecoderUsing(
    decodingPipeline: UtfDecodingPipeline,
    sourceCharset: Charset,
    byteGenerator: Gen[Has[Random] with Has[Sized], Chunk[Byte]],
    bom: Chunk[Byte] = Chunk.empty
  ) =
    check(byteGenerator, Gen.int) {
      // Enabling `rechunk(chunkSize)` makes this suite run for too long and
      // could potentially cause OOM during builds. However, running the tests with
      // `rechunk(chunkSize)` can guarantee that different chunks have no impact on
      // the functionality of decoders. You should run it at least once locally before
      // pushing your commit.
      (generatedBytes, /*chunkSize*/ _) =>
        val originalBytes = fixIfGeneratedBytesBeginWithBom(generatedBytes, sourceCharset, bom)
        ZStream
          .fromChunk(bom ++ originalBytes)
//          .rechunk(chunkSize)
          .via(decodingPipeline)
          .mkString
          .map { decodedString =>
            val roundTripBytes = stringToByteChunkOf(sourceCharset, decodedString)

            assertTrue(originalBytes == roundTripBytes)
          }
    }

  private def testDecoderWithRandomStringUsing(
    decodingPipeline: UtfDecodingPipeline,
    sourceCharset: Charset,
    bom: Chunk[Byte] = Chunk.empty,
    stringGenerator: Gen[Has[Random] with Has[Sized], String] = Gen.string
  ) =
    testDecoderUsing(
      decodingPipeline,
      sourceCharset,
      stringGenerator.map(stringToByteChunkOf(sourceCharset, _)),
      bom
    )

  private def testEncoderWithRandomStringUsing(
    textDecodingPipeline: UtfDecodingPipeline,
    encoderUnderTest: UtfEncodingPipeline,
    sourceCharset: Charset,
    bom: Chunk[Byte] = Chunk.empty
  ) =
    check(
      Gen.string.map(stringToByteChunkOf(sourceCharset, _))
    ) { generatedBytes =>
      val originalBytes = generatedBytes
      ZStream
        .fromChunk(originalBytes)
        .via(textDecodingPipeline)
        .via(encoderUnderTest)
        .runCollect
        .map { roundTripBytes =>
          assertTrue((bom ++ originalBytes) == roundTripBytes)
        }
    }

  private def runOnlyIfSupporting(charset: String) =
    if (Charset.isSupported(charset)) jvmOnly
    else ignore

  override def spec =
    suite("TextCodecPipelineSpec")(
      suite("Text Decoders")(
        test("iso_8859_1Decode") {
          testDecoderWithRandomStringUsing(
            ZPipeline.iso_8859_1Decode,
            StandardCharsets.ISO_8859_1,
            stringGenerator = Gen.iso_8859_1
          )
        } @@ runOnlyIfSupporting(StandardCharsets.ISO_8859_1.name),
        test("usASCIIDecode") {
          testDecoderWithRandomStringUsing(ZPipeline.usASCIIDecode, StandardCharsets.US_ASCII)
        } @@ runOnlyIfSupporting(StandardCharsets.US_ASCII.name),
        suite("utfDecode")(
          test("UTF-8 with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_8, BOM.Utf8)
          },
          test("UTF-8 without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_8)
          } @@ runOnlyIfSupporting(StandardCharsets.UTF_8.name),
          test("UTF-8 with BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utfDecode,
              StandardCharsets.UTF_8,
              Gen.const(BOM.Utf8 ++ Chunk[Byte](97, 98)),
              BOM.Utf8
            )
          },
          test("UTF-8 without BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utfDecode,
              StandardCharsets.UTF_8,
              Gen.const(BOM.Utf8 ++ Chunk[Byte](97, 98))
            )
          },
          test("UTF-16BE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_16BE, BOM.Utf16BE)
          } @@ runOnlyIfSupporting(StandardCharsets.UTF_16BE.name),
          test("UTF-16BE with BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utfDecode,
              StandardCharsets.UTF_16BE,
              Gen.const(BOM.Utf16BE ++ Chunk[Byte](0, 97, 0, 98)),
              BOM.Utf16BE
            )
          } @@ runOnlyIfSupporting(StandardCharsets.UTF_16BE.name),
          test("UTF-16LE with BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utfDecode, StandardCharsets.UTF_16LE, BOM.Utf16LE)
          } @@ runOnlyIfSupporting(StandardCharsets.UTF_16LE.name),
          test("UTF-16LE with BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utfDecode,
              StandardCharsets.UTF_16LE,
              Gen.const(BOM.Utf16LE ++ Chunk[Byte](97, 0, 98, 0)),
              BOM.Utf16LE
            )
          } @@ runOnlyIfSupporting(StandardCharsets.UTF_16LE.name),
          test("UTF-32BE with BOM") {
            testDecoderWithRandomStringUsing(
              ZPipeline.utfDecode,
              CharsetUtf32BE,
              BOM.Utf32BE
            )
          } @@ runOnlyIfSupporting("UTF-32BE"),
          test("UTF-32BE with BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utfDecode,
              CharsetUtf32BE,
              Gen.const(BOM.Utf32BE ++ Chunk[Byte](0, 0, 0, 97, 0, 0, 0, 98)),
              BOM.Utf32BE
            )
          } @@ runOnlyIfSupporting("UTF-32BE"),
          test("UTF-32LE with BOM") {
            testDecoderWithRandomStringUsing(
              ZPipeline.utfDecode,
              CharsetUtf32LE,
              BOM.Utf32LE
            )
          } @@ runOnlyIfSupporting("UTF-32LE"),
          test("UTF-32LE with BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utfDecode,
              CharsetUtf32LE,
              Gen.const(BOM.Utf32LE ++ Chunk[Byte](97, 0, 0, 0, 98, 0, 0, 0)),
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
          } @@ runOnlyIfSupporting(StandardCharsets.UTF_8.name),
          test("Data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf8Decode,
              StandardCharsets.UTF_8,
              Gen.const(BOM.Utf8 ++ Chunk[Byte](97, 98))
            )
          },
          test("Data that happens to start with BOM, with BOM prepended") {
            testDecoderUsing(
              ZPipeline.utf8Decode,
              StandardCharsets.UTF_8,
              Gen.const(BOM.Utf8 ++ Chunk[Byte](97, 98)),
              BOM.Utf8
            )
          }
        ),
        suite("utf16BEDecode")(
          test("Random data") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16BEDecode, StandardCharsets.UTF_16BE)
          },
          test("Data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf16BEDecode,
              StandardCharsets.UTF_16BE,
              Gen.const(BOM.Utf16BE ++ Chunk[Byte](0, 97, 0, 98))
            )
          }
        ) @@ runOnlyIfSupporting(StandardCharsets.UTF_16BE.name),
        suite("utf16LEDecode")(
          test("Random data") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16LEDecode, StandardCharsets.UTF_16LE)
          },
          test("Data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf16LEDecode,
              StandardCharsets.UTF_16LE,
              Gen.const(BOM.Utf16LE ++ Chunk[Byte](97, 0, 98, 0))
            )
          }
        ) @@ runOnlyIfSupporting(StandardCharsets.UTF_16LE.name),
        suite("utf16Decode")(
          test("UTF-16 without BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16)
          },
          test("UTF-16 without BOM but data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf16Decode,
              StandardCharsets.UTF_16,
              Gen.const(BOM.Utf16BE ++ Chunk[Byte](0, 97, 0, 98))
            )
          },
          test("UTF-16BE without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16BE)
          },
          test("UTF-16BE without BOM but data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf16Decode,
              StandardCharsets.UTF_16BE,
              Gen.const(BOM.Utf16BE ++ Chunk[Byte](0, 97, 0, 98))
            )
          },
          test("Big Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16BE, BOM.Utf16BE)
          },
          test("Big Endian BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf16Decode,
              StandardCharsets.UTF_16BE,
              Gen.const(BOM.Utf16BE ++ Chunk[Byte](0, 97, 0, 98)),
              BOM.Utf16BE
            )
          },
          test("Little Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf16Decode, StandardCharsets.UTF_16LE, BOM.Utf16LE)
          },
          test("Little Endian BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf16Decode,
              StandardCharsets.UTF_16LE,
              Gen.const(BOM.Utf16LE ++ Chunk[Byte](97, 0, 98, 0)),
              BOM.Utf16LE
            )
          }
        ) @@ runOnlyIfSupporting(StandardCharsets.UTF_16.name),
        suite("utf32BEDecode")(
          test("Random data") {
            testDecoderWithRandomStringUsing(
              ZPipeline.utf32BEDecode,
              CharsetUtf32BE
            )
          },
          test("Data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf32BEDecode,
              CharsetUtf32BE,
              Gen.const(BOM.Utf32BE ++ Chunk[Byte](0, 0, 0, 97, 0, 0, 0, 98))
            )
          },
          test("Data that happens to start with BOM, with BOM prepended") {
            testDecoderUsing(
              ZPipeline.utf32BEDecode,
              CharsetUtf32BE,
              Gen.const(BOM.Utf32BE ++ Chunk[Byte](0, 0, 0, 97, 0, 0, 0, 98)),
              BOM.Utf32BE
            )
          }
        ) @@ runOnlyIfSupporting("UTF-32BE"),
        suite("utf32LEDecode")(
          test("Random data") {
            testDecoderWithRandomStringUsing(
              ZPipeline.utf32LEDecode,
              CharsetUtf32LE
            )
          },
          test("Data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf32LEDecode,
              CharsetUtf32LE,
              Gen.const(BOM.Utf32LE ++ Chunk[Byte](97, 0, 0, 0, 98, 0, 0, 0))
            )
          },
          test("Data that happens to start with BOM, with BOM prepended") {
            testDecoderUsing(
              ZPipeline.utf32LEDecode,
              CharsetUtf32LE,
              Gen.const(BOM.Utf32LE ++ Chunk[Byte](97, 0, 0, 0, 98, 0, 0, 0)),
              BOM.Utf32LE
            )
          }
        ) @@ runOnlyIfSupporting("UTF-32LE"),
        suite("utf32Decode")(
          test("UTF-32 without BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32)
          },
          test("UTF-32 without BOM but data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf32Decode,
              CharsetUtf32,
              Gen.const(BOM.Utf32BE ++ Chunk[Byte](0, 0, 0, 97, 0, 0, 0, 98))
            )
          },
          test("UTF-32BE without BOM (default)") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32BE)
          },
          test("UTF-32BE without BOM but data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf32Decode,
              CharsetUtf32BE,
              Gen.const(BOM.Utf32BE ++ Chunk[Byte](0, 0, 0, 97, 0, 0, 0, 98))
            )
          },
          test("Big Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32BE, BOM.Utf32BE)
          },
          test("Big Endian BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf32Decode,
              CharsetUtf32BE,
              Gen.const(BOM.Utf32BE ++ Chunk[Byte](0, 0, 0, 97, 0, 0, 0, 98)),
              BOM.Utf32BE
            )
          },
          test("Little Endian BOM") {
            testDecoderWithRandomStringUsing(ZPipeline.utf32Decode, CharsetUtf32LE, BOM.Utf32LE)
          },
          test("Little Endian BOM, with data that happens to start with BOM") {
            testDecoderUsing(
              ZPipeline.utf32Decode,
              CharsetUtf32LE,
              Gen.const(BOM.Utf32LE ++ Chunk[Byte](97, 0, 0, 0, 98, 0, 0, 0)),
              BOM.Utf32LE
            )
          }
        ) @@ runOnlyIfSupporting("UTF-32")
      ),
      suite("Text Encoders")(
        test("iso_8859_1Encode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.iso_8859_1Decode,
            ZPipeline.iso_8859_1Encode,
            StandardCharsets.ISO_8859_1
          )
        },
        test("usASCIIDecode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.usASCIIDecode,
            ZPipeline.usASCIIEncode,
            StandardCharsets.US_ASCII
          )
        },
        test("utf8Encode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf8Decode,
            ZPipeline.utf8Encode,
            StandardCharsets.UTF_8
          )
        },
        test("utf8WithBomEncode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf8Decode,
            ZPipeline.utf8WithBomEncode,
            StandardCharsets.UTF_8,
            BOM.Utf8
          )
        },
        test("utf16BEEncode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf16BEDecode,
            ZPipeline.utf16BEEncode,
            StandardCharsets.UTF_16BE,
            BOM.Utf16BE
          )
        },
        test("utf16LEEncode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf16LEDecode,
            ZPipeline.utf16LEEncode,
            StandardCharsets.UTF_16LE,
            BOM.Utf16LE
          )
        },
        test("utf16Encode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf16Decode,
            ZPipeline.utf16Encode,
            StandardCharsets.UTF_16BE
          )
        },
        test("utf32BEEncode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf32BEDecode,
            ZPipeline.utf32BEEncode,
            CharsetUtf32BE,
            BOM.Utf32BE
          )
        } @@ runOnlyIfSupporting("UTF-32BE"),
        test("utf32LEEncode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf32LEDecode,
            ZPipeline.utf32LEEncode,
            CharsetUtf32LE,
            BOM.Utf32LE
          )
        } @@ runOnlyIfSupporting("UTF-32LE"),
        test("utf32Encode") {
          testEncoderWithRandomStringUsing(
            ZPipeline.utf32Decode,
            ZPipeline.utf32Encode,
            CharsetUtf32BE
          )
        } @@ runOnlyIfSupporting("UTF-32")
      )
    ) @@ nondeterministic
}

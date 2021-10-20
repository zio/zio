package zio.stream

import ZTransducer.{BOM, CharsetUtf32, CharsetUtf32BE, CharsetUtf32LE}

import zio._
import zio.test._

import java.io.{IOException, InputStream}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path, Paths}

object ZTransducerPlatformSpecificSpec extends ZIOBaseSpec {
  private val bomTestFilesPath: Path = Paths.get("zio/stream/bom")

  private val classLoader: ClassLoader = Thread.currentThread.getContextClassLoader

  private def inputStreamForResource(path: Path): InputStream =
    // Resource paths are always '/'
    classLoader.getResourceAsStream(path.toString.replace('\\', '/'))

  private def readResource(fileName: String) =
    ZStream.fromInputStream(
      inputStreamForResource(bomTestFilesPath.resolve(fileName))
    )

  private def readResourceAsString(fileName: String, transducer: ZTransducer[Any, IOException, Byte, String]) =
    readResource(fileName)
      .transduce(transducer)
      .runCollect
      .map(_.mkString)

  private val QuickBrownTest = readResourceAsString("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utf8Decode)

  private def testDecoderUsing(fileName: String, transducer: ZTransducer[Any, IOException, Byte, String]) =
    readResourceAsString(fileName, transducer)
      .zipWith(QuickBrownTest) { (l, r) =>
        assertTrue(l == r)
      }

  private def stringToByteChunkOf(charset: Charset, source: String) =
    Chunk.fromArray(source.getBytes(charset))

  private def testDecoderWithRandomStringUsing(
    transducerUnderTest: ZTransducer[Any, IOException, Byte, String],
    sourceCharset: Charset,
    withBom: Chunk[Byte] => Chunk[Byte] = identity
  ) =
    check(
      Gen.string.map(stringToByteChunkOf(sourceCharset, _))
    ) { originalValue =>
      ZStream
        .fromChunk(withBom(originalValue))
        .transduce(transducerUnderTest)
        .runCollect
        .map(_.mkString)
        .map { decodedString =>
          assertTrue(originalValue == stringToByteChunkOf(sourceCharset, decodedString))
        }
    }

  private def testEncoderUsing(
    fileName: String,
    transduceByteToString: ZTransducer[Any, IOException, Byte, String],
    transducerUnderTest: ZTransducer[Any, IOException, String, Byte]
  ) = {
    val textFile = fileName + ".txt"
    for {
      originalBytes <- readResource(textFile).runCollect
      roundTripBytes <- readResource(textFile)
                          .transduce(transduceByteToString)
                          .transduce(transducerUnderTest)
                          .runCollect
    } yield assertTrue(originalBytes == roundTripBytes)
  }

  private def testEncoderWithRandomStringUsing(
    transduceByteToString: ZTransducer[Any, IOException, Byte, String],
    transducerUnderTest: ZTransducer[Any, IOException, String, Byte],
    sourceCharset: Charset,
    withBom: Chunk[Byte] => Chunk[Byte] = identity
  ) =
    check(
      Gen.string.map(stringToByteChunkOf(sourceCharset, _))
    ) { generatedBytes =>
      val originalBytes = withBom(generatedBytes)
      ZStream
        .fromChunk(originalBytes)
        .transduce(transduceByteToString)
        .transduce(transducerUnderTest)
        .runCollect
        .map { roundTripBytes =>
          assertTrue(originalBytes == roundTripBytes)
        }
    }

  private def runOnlyIfSupporting(charset: String) =
    if (Charset.isSupported(charset)) TestAspect.jvmOnly
    else TestAspect.ignore

  override def spec: ZSpec[Environment, Failure] = suite("ZSink JVM")(
    suite("fromFile")(
      test("writes to an existing file") {
        val data = (0 to 100).mkString

        Task(Files.createTempFile("stream", "fromFile"))
          .acquireReleaseWith(path => Task(Files.delete(path)).orDie) { path =>
            for {
              bytes  <- Task(data.getBytes(StandardCharsets.UTF_8))
              length <- ZStream.fromIterable(bytes).run(ZSink.fromFile(path))
              str    <- Task(new String(Files.readAllBytes(path)))
            } yield {
              assertTrue(data == str) &&
              assertTrue(bytes.length.toLong == length)
            }
          }
      }
    ),
    suite("ZTransducer.iso_8859_1Decode")(
      test("using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.iso_8859_1Decode,
          StandardCharsets.ISO_8859_1
        )
      }
    ),
    suite("ZTransducer.usASCIIDecode")(
      test("using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.usASCIIDecode,
          StandardCharsets.US_ASCII
        )
      }
    ),
    suite("ZTransducer.utfDecode")(
      test("UTF-8 with BOM") {
        testDecoderUsing("quickbrown-UTF-8-with-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-8 with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utfDecode,
          StandardCharsets.UTF_8,
          BOM.Utf8 ++ _
        )
      },
      test("UTF-8 w/o BOM") {
        testDecoderUsing("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-8 w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utfDecode,
          StandardCharsets.UTF_8
        )
      },
      test("UTF-16BE with BOM") {
        testDecoderUsing("quickbrown-UTF-16BE-with-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-16BE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utfDecode,
          StandardCharsets.UTF_16BE,
          BOM.Utf16BE ++ _
        )
      },
      test("UTF-16LE with BOM") {
        testDecoderUsing("quickbrown-UTF-16LE-with-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-16LE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utfDecode,
          StandardCharsets.UTF_16LE,
          BOM.Utf16LE ++ _
        )
      },
      test("UTF-32BE with BOM") {
        testDecoderUsing("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utfDecode)
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32BE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utfDecode,
          CharsetUtf32BE,
          BOM.Utf32BE ++ _
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32BE with BOM, using `utf32BEDecode`") {
        testDecoderUsing("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utf32BEDecode)
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32LE with BOM") {
        testDecoderUsing("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utfDecode)
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32LE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utfDecode,
          CharsetUtf32LE,
          BOM.Utf32LE ++ _
        )
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32LE with BOM, using `utf32LEDecode`") {
        testDecoderUsing("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utf32LEDecode)
      } @@ runOnlyIfSupporting("UTF-32LE")
    ),
    suite("ZTransducer.utf8Decode")(
      test("UTF-8 with BOM") {
        testDecoderUsing("quickbrown-UTF-8-with-BOM.txt", ZTransducer.utf8Decode)
      },
      test("UTF-8 with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf8Decode,
          StandardCharsets.UTF_8,
          BOM.Utf8 ++ _
        )
      },
      test("UTF-8 w/o BOM") {
        testDecoderUsing("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utf8Decode)
      },
      test("UTF-8 w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf8Decode,
          StandardCharsets.UTF_8
        )
      }
    ),
    suite("ZTransducer.utf16Decode")(
      test("UTF-16BE with BOM") {
        testDecoderUsing("quickbrown-UTF-16BE-with-BOM.txt", ZTransducer.utf16Decode)
      },
      test("UTF-16BE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          StandardCharsets.UTF_16BE,
          BOM.Utf16BE ++ _
        )
      },
      test("UTF-16LE with BOM") {
        testDecoderUsing("quickbrown-UTF-16LE-with-BOM.txt", ZTransducer.utf16Decode)
      },
      test("UTF-16LE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          StandardCharsets.UTF_16LE,
          BOM.Utf16LE ++ _
        )
      },
      test("UTF-16BE w/o BOM (default)") {
        testDecoderUsing("quickbrown-UTF-16BE-no-BOM.txt", ZTransducer.utf16Decode)
      },
      test("UTF-16BE w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          StandardCharsets.UTF_16BE
        )
      },
      test("UTF-16, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          StandardCharsets.UTF_16
        )
      }
    ),
    suite("ZTransducer.utf16BEDecode")(
      test("UTF-16BE w/o BOM") {
        testDecoderUsing("quickbrown-UTF-16BE-no-BOM.txt", ZTransducer.utf16BEDecode)
      },
      test("UTF-16BE w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf16BEDecode,
          StandardCharsets.UTF_16BE
        )
      }
    ),
    suite("ZTransducer.utf16LEDecode")(
      test("UTF-16LE w/o BOM") {
        testDecoderUsing("quickbrown-UTF-16LE-no-BOM.txt", ZTransducer.utf16LEDecode)
      },
      test("UTF-16LE w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf16LEDecode,
          StandardCharsets.UTF_16LE
        )
      }
    ),
    suite("ZTransducer.utf32Decode")(
      test("UTF-32BE with BOM") {
        testDecoderUsing("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utf32Decode)
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32BE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          CharsetUtf32BE,
          BOM.Utf32BE ++ _
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32LE with BOM") {
        testDecoderUsing("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utf32Decode)
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32LE with BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          CharsetUtf32LE,
          BOM.Utf32LE ++ _
        )
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32BE w/o BOM (default)") {
        testDecoderUsing("quickbrown-UTF-32BE-no-BOM.txt", ZTransducer.utf32Decode)
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32BE w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          CharsetUtf32BE
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          CharsetUtf32
        )
      } @@ runOnlyIfSupporting("UTF-32")
    ),
    suite("ZTransducer.utf32BEDecode")(
      test("UTF-32BE w/o BOM") {
        testDecoderUsing("quickbrown-UTF-32BE-no-BOM.txt", ZTransducer.utf32BEDecode)
      },
      test("UTF-32BE w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf32BEDecode,
          CharsetUtf32BE
        )
      }
    ) @@ runOnlyIfSupporting("UTF-32BE"),
    suite("ZTransducer.utf32LEDecode")(
      test("UTF-32LE w/o BOM") {
        testDecoderUsing("quickbrown-UTF-32LE-no-BOM.txt", ZTransducer.utf32LEDecode)
      },
      test("UTF-32LE w/o BOM, using random strings") {
        testDecoderWithRandomStringUsing(
          ZTransducer.utf32LEDecode,
          CharsetUtf32LE
        )
      }
    ) @@ runOnlyIfSupporting("UTF-32LE"),
    suite("Encoding from String to Byte")(
      test("iso_8859_1Encode, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.iso_8859_1Decode,
          ZTransducer.iso_8859_1Encode,
          StandardCharsets.ISO_8859_1
        )
      },
      test("usASCIIEncode, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.usASCIIDecode,
          ZTransducer.usASCIIEncode,
          StandardCharsets.US_ASCII
        )
      },
      test("UTF-8 w/o BOM") {
        testEncoderUsing(
          "quickbrown-UTF-8-no-BOM",
          ZTransducer.utf8Decode,
          ZTransducer.utf8Encode
        )
      },
      test("UTF-8 w/o BOM, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf8Decode,
          ZTransducer.utf8Encode,
          StandardCharsets.UTF_8
        )
      },
      test("UTF-8 with BOM") {
        testEncoderUsing(
          "quickbrown-UTF-8-with-BOM",
          ZTransducer.utf8Decode,
          ZTransducer.utf8BomEncode
        )
      },
      test("UTF-8 with BOM, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf8Decode,
          ZTransducer.utf8BomEncode,
          StandardCharsets.UTF_8,
          BOM.Utf8 ++ _
        )
      },
      test("UTF-16BE") {
        testEncoderUsing(
          "quickbrown-UTF-16BE-with-BOM",
          ZTransducer.utf16Decode,
          ZTransducer.utf16BEEncode
        )
      },
      test("UTF-16BE, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          ZTransducer.utf16BEEncode,
          StandardCharsets.UTF_16BE,
          BOM.Utf16BE ++ _
        )
      },
      test("UTF-16LE") {
        testEncoderUsing(
          "quickbrown-UTF-16LE-with-BOM",
          ZTransducer.utf16Decode,
          ZTransducer.utf16LEEncode
        )
      },
      test("UTF-16LE, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          ZTransducer.utf16LEEncode,
          StandardCharsets.UTF_16LE,
          BOM.Utf16LE ++ _
        )
      },
      test("UTF-16") {
        testEncoderUsing(
          "quickbrown-UTF-16BE-no-BOM",
          ZTransducer.utf16Decode,
          ZTransducer.utf16Encode
        )
      },
      test("UTF-16, using random strings and Utf16BE charset") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf16Decode,
          ZTransducer.utf16Encode,
          StandardCharsets.UTF_16BE
        )
      },
      test("UTF-32BE") {
        testEncoderUsing(
          "quickbrown-UTF-32BE-with-BOM",
          ZTransducer.utf32Decode,
          ZTransducer.utf32BEEncode
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32BE, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          ZTransducer.utf32BEEncode,
          CharsetUtf32BE,
          BOM.Utf32BE ++ _
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32LE") {
        testEncoderUsing(
          "quickbrown-UTF-32LE-with-BOM",
          ZTransducer.utf32Decode,
          ZTransducer.utf32LEEncode
        )
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32LE, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          ZTransducer.utf32LEEncode,
          CharsetUtf32LE,
          BOM.Utf32LE ++ _
        )
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32") {
        testEncoderUsing(
          "quickbrown-UTF-32BE-no-BOM",
          ZTransducer.utf32Decode,
          ZTransducer.utf32Encode
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32, using random strings") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          ZTransducer.utf32Encode,
          CharsetUtf32
        )
      } @@ runOnlyIfSupporting("UTF-32"),
      test("UTF-32, using random strings and Utf32BE charset") {
        testEncoderWithRandomStringUsing(
          ZTransducer.utf32Decode,
          ZTransducer.utf32Encode,
          CharsetUtf32BE
        )
      } @@ runOnlyIfSupporting("UTF-32BE")
    )
  )
}

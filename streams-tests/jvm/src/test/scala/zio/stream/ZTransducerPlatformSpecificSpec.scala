package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import java.io.{IOException, InputStream}
import java.nio.charset.Charset
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

  private def testEncoderUsing(fileName: String, transducer: ZTransducer[Any, IOException, Byte, Byte]) = {
    val textFile = fileName + ".txt"
    for {
      originalBytes  <- readResource(textFile).runCollect
      roundTripBytes <- readResource(textFile).transduce(transducer).runCollect
    } yield {
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
              bytes  <- Task(data.getBytes("UTF-8"))
              length <- ZStream.fromIterable(bytes).run(ZSink.fromFile(path))
              str    <- Task(new String(Files.readAllBytes(path)))
            } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
          }
      }
    ),
    suite("ZTransducer.utfDecode")(
      test("UTF-8 with BOM") {
        testDecoderUsing("quickbrown-UTF-8-with-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-8 no BOM") {
        testDecoderUsing("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-16BE with BOM") {
        testDecoderUsing("quickbrown-UTF-16BE-with-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-16LE with BOM") {
        testDecoderUsing("quickbrown-UTF-16LE-with-BOM.txt", ZTransducer.utfDecode)
      },
      test("UTF-32BE with BOM") {
        testDecoderUsing("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utf32BEDecode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
      test("UTF-32BE with BOM (using `utfDecode`)") {
        testDecoderUsing("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utfDecode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
      test("UTF-32LE with BOM") {
        testDecoderUsing("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utf32LEDecode)
      } @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore),
      test("UTF-32LE with BOM (using `utfDecode`)") {
        testDecoderUsing("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utfDecode)
      } @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore)
    ),
    suite("ZTransducer.utf8Decode")(
      test("UTF-8 with BOM") {
        testDecoderUsing("quickbrown-UTF-8-with-BOM.txt", ZTransducer.utf8Decode)
      },
      test("UTF-8 no BOM") {
        testDecoderUsing("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utf8Decode)
      }
    ),
    suite("ZTransducer.utf16Decode")(
      test("UTF-16BE with BOM") {
        testDecoderUsing("quickbrown-UTF-16BE-with-BOM.txt", ZTransducer.utf16Decode)
      },
      test("UTF-16LE with BOM") {
        testDecoderUsing("quickbrown-UTF-16LE-with-BOM.txt", ZTransducer.utf16Decode)
      },
      test("UTF-16BE no BOM (default)") {
        testDecoderUsing("quickbrown-UTF-16BE-no-BOM.txt", ZTransducer.utf16Decode)
      }
    ),
    suite("ZTransducer.utf16BEDecode")(
      test("UTF-16BE no BOM") {
        testDecoderUsing("quickbrown-UTF-16BE-no-BOM.txt", ZTransducer.utf16BEDecode)
      }
    ),
    suite("ZTransducer.utf16LEDecode")(
      test("UTF-16LE no BOM") {
        testDecoderUsing("quickbrown-UTF-16LE-no-BOM.txt", ZTransducer.utf16LEDecode)
      }
    ),
    suite("ZTransducer.utf32Decode")(
      test("UTF-32BE with BOM") {
        testDecoderUsing("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utf32Decode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
      test("UTF-32LE with BOM") {
        testDecoderUsing("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utf32Decode)
      } @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore),
      test("UTF-32BE no BOM (default)") {
        testDecoderUsing("quickbrown-UTF-32BE-no-BOM.txt", ZTransducer.utf32Decode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore)
    ),
    suite("ZTransducer.utf32BEDecode")(
      test("UTF-32BE no BOM") {
        testDecoderUsing("quickbrown-UTF-32BE-no-BOM.txt", ZTransducer.utf32BEDecode)
      }
    ) @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
    suite("ZTransducer.utf32LEDecode")(
      test("UTF-32LE no BOM") {
        testDecoderUsing("quickbrown-UTF-32LE-no-BOM.txt", ZTransducer.utf32LEDecode)
      }
    ) @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore),
    suite("Encoding from String to Byte")(
      test("UTF-8 with no BOM") {
        testEncoderUsing(
          "quickbrown-UTF-8-no-BOM",
          ZTransducer.utf8Decode >>> ZTransducer.utf8Encode
        )
      },
      test("UTF-8 with BOM") {
        testEncoderUsing(
          "quickbrown-UTF-8-with-BOM",
          ZTransducer.utf8Decode >>> ZTransducer.utf8BomEncode
        )
      },
      test("UTF-16BE") {
        testEncoderUsing(
          "quickbrown-UTF-16BE-with-BOM",
          ZTransducer.utf16Decode >>> ZTransducer.utf16BEEncode
        )
      },
      test("UTF-16LE") {
        testEncoderUsing(
          "quickbrown-UTF-16LE-with-BOM",
          ZTransducer.utf16Decode >>> ZTransducer.utf16LEEncode
        )
      },
      test("UTF-16") {
        testEncoderUsing(
          "quickbrown-UTF-16BE-with-BOM",
          ZTransducer.utf16Decode >>> ZTransducer.utf16Encode
        )
      },
      test("UTF-32BE") {
        testEncoderUsing(
          "quickbrown-UTF-32BE-with-BOM",
          ZTransducer.utf32Decode >>> ZTransducer.utf32BEEncode
        )
      } @@ runOnlyIfSupporting("UTF-32BE"),
      test("UTF-32LE") {
        testEncoderUsing(
          "quickbrown-UTF-32LE-with-BOM",
          ZTransducer.utf32Decode >>> ZTransducer.utf32LEEncode
        )
      } @@ runOnlyIfSupporting("UTF-32LE"),
      test("UTF-32") {
        testEncoderUsing(
          "quickbrown-UTF-32BE-with-BOM",
          ZTransducer.utf32Decode >>> ZTransducer.utf32Encode
        )
      } @@ runOnlyIfSupporting("UTF-32BE")
    )
  )
}

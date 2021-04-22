package zio.stream

import zio.test.Assertion._
import zio.test._
import zio._

import java.io.{IOException, InputStream}
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}

object ZTransducerPlatformSpecificSpec extends ZIOBaseSpec {
  private val bomTestFilesPath: Path = Paths.get("zio/stream/bom")

  private val classLoader: ClassLoader = Thread.currentThread.getContextClassLoader

  private def inputStreamForResource(path: Path): InputStream =
    // Resource paths are always '/'
    classLoader.getResourceAsStream(path.toString.replace('\\', '/'))

  private def readResourceAsString(
    fileName: String,
    transducer: ZTransducer[Has[Blocking], IOException, Byte, String]
  ) =
    ZStream
      .fromInputStream(inputStreamForResource(bomTestFilesPath.resolve(fileName)))
      .transduce(transducer)
      .runCollect
      .map(_.mkString)

  private val QuickBrownTest = readResourceAsString("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utf8Decode)

  private def testEncoding(fileName: String, transducer: ZTransducer[Has[Blocking], IOException, Byte, String]) =
    readResourceAsString(fileName, transducer)
      .zipWith(QuickBrownTest)((l, r) => assert(l)(equalTo(r)))

  override def spec: ZSpec[Environment, Failure] = suite("ZSink JVM")(
    suite("fromFile")(
      testM("writes to an existing file") {
        val data = (0 to 100).mkString

        Task(Files.createTempFile("stream", "fromFile"))
          .bracket(path => Task(Files.delete(path)).orDie) { path =>
            for {
              bytes  <- Task(data.getBytes("UTF-8"))
              length <- ZStream.fromIterable(bytes).run(ZSink.fromFile(path))
              str    <- Task(new String(Files.readAllBytes(path)))
            } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
          }
      }
    ),
    suite("ZTransducer.utfDecode")(
      testM("UTF-8 with BOM") {
        testEncoding("quickbrown-UTF-8-with-BOM.txt", ZTransducer.utfDecode)
      },
      testM("UTF-8 no BOM") {
        testEncoding("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utfDecode)
      },
      testM("UTF-16BE with BOM") {
        testEncoding("quickbrown-UTF-16BE-with-BOM.txt", ZTransducer.utfDecode)
      },
      testM("UTF-16LE with BOM") {
        testEncoding("quickbrown-UTF-16LE-with-BOM.txt", ZTransducer.utfDecode)
      },
      testM("UTF-32BE with BOM") {
        testEncoding("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utf32BEDecode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
      testM("UTF-32LE with BOM") {
        testEncoding("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utf32LEDecode)
      } @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore)
    ),
    suite("ZTransducer.utf8Decode")(
      testM("UTF-8 with BOM") {
        testEncoding("quickbrown-UTF-8-with-BOM.txt", ZTransducer.utf8Decode)
      },
      testM("UTF-8 no BOM") {
        testEncoding("quickbrown-UTF-8-no-BOM.txt", ZTransducer.utf8Decode)
      }
    ),
    suite("ZTransducer.utf16Decode")(
      testM("UTF-16BE with BOM") {
        testEncoding("quickbrown-UTF-16BE-with-BOM.txt", ZTransducer.utf16Decode)
      },
      testM("UTF-16LE with BOM") {
        testEncoding("quickbrown-UTF-16LE-with-BOM.txt", ZTransducer.utf16Decode)
      },
      testM("UTF-16BE no BOM (default)") {
        testEncoding("quickbrown-UTF-16BE-no-BOM.txt", ZTransducer.utf16Decode)
      }
    ),
    suite("ZTransducer.utf16BEDecode")(
      testM("UTF-16BE no BOM") {
        testEncoding("quickbrown-UTF-16BE-no-BOM.txt", ZTransducer.utf16BEDecode)
      }
    ),
    suite("ZTransducer.utf16LEDecode")(
      testM("UTF-16LE no BOM") {
        testEncoding("quickbrown-UTF-16LE-no-BOM.txt", ZTransducer.utf16LEDecode)
      }
    ),
    suite("ZTransducer.utf32Decode")(
      testM("UTF-32BE with BOM") {
        testEncoding("quickbrown-UTF-32BE-with-BOM.txt", ZTransducer.utf32Decode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
      testM("UTF-32LE with BOM") {
        testEncoding("quickbrown-UTF-32LE-with-BOM.txt", ZTransducer.utf32Decode)
      } @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore),
      testM("UTF-32BE no BOM (default)") {
        testEncoding("quickbrown-UTF-32BE-no-BOM.txt", ZTransducer.utf32Decode)
      } @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore)
    ),
    suite("ZTransducer.utf32BEDecode")(
      testM("UTF-32BE no BOM") {
        testEncoding("quickbrown-UTF-32BE-no-BOM.txt", ZTransducer.utf32BEDecode)
      }
    ) @@ (if (Charset.isSupported("UTF-32BE")) TestAspect.jvmOnly else TestAspect.ignore),
    suite("ZTransducer.utf32BEDecode")(
      testM("UTF-32LE no BOM") {
        testEncoding("quickbrown-UTF-32LE-no-BOM.txt", ZTransducer.utf32LEDecode)
      }
    ) @@ (if (Charset.isSupported("UTF-32LE")) TestAspect.jvmOnly else TestAspect.ignore)
  )
}

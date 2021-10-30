package zio.stream.experimental

import zio._
import zio.test._
import zio.test.Assertion._
import java.io.ByteArrayOutputStream

object ZSinkPlatformSpecificSpec extends ZIOBaseSpec {

  override def spec: ZSpec[Any, Throwable] = suite("ZSink JVM experimental")(
    suite("fromOutputStream")(
      test("writes to byte array output stream") {
        val data = (0 to 100).mkString

        for {
          bytes  <- Task(data.getBytes("UTF-8"))
          os      = new ByteArrayOutputStream(data.length)
          length <- ZStream.fromIterable(bytes).run(ZSink.fromOutputStream(os))
          str    <- Task(os.toString("UTF-8"))
        } yield assert(data)(equalTo(str)) && assert(bytes.length.toLong)(equalTo(length))
      }

    )
  )

}

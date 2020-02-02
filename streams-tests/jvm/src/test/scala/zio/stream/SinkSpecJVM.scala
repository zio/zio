package zio.stream

import scala.{ Stream => _ }

import zio._
import zio.test.Assertion.equalTo
import zio.test._

object SinkSpecJVM extends ZIOBaseSpec {

  def spec = suite("SinkSpecJVM")(
    testM("fromOutputStream") {
      import java.io.ByteArrayOutputStream

      val output = new ByteArrayOutputStream()
      val data   = "0123456789"
      val stream = Stream(Chunk.fromArray(data.take(5).getBytes), Chunk.fromArray(data.drop(5).getBytes))

      for {
        bytesWritten <- stream.run(ZSink.fromOutputStream(output))
      } yield assert(bytesWritten)(equalTo(10)) && assert(new String(output.toByteArray, "UTF-8"))(equalTo(data))
    }
  )
}

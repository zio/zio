package zio
package interop

import zio.test.Assertion._
import zio.test._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel}

object JavaSpecJVM extends ZIOBaseSpec {

  import ZIOTag._

  def spec: Spec[Any, Any] = suite("JavaSpecJVM")(
    suite("`ZIO.withCompletionHandler` must")(
      test("write and read to and from AsynchronousSocketChannel") {
        val list: List[Byte] = List(13)
        val address          = new InetSocketAddress(54321)
        val server           = AsynchronousServerSocketChannel.open().bind(address)
        val client           = AsynchronousSocketChannel.open()

        val taskServer = for {
          c <- ZIO.asyncWithCompletionHandler[AsynchronousSocketChannel](server.accept((), _))
          w <- ZIO.asyncWithCompletionHandler[Integer](c.write(ByteBuffer.wrap(list.toArray), (), _))
        } yield w

        val taskClient = for {
          _     <- ZIO.asyncWithCompletionHandler[Void](client.connect(address, (), _))
          buffer = ByteBuffer.allocate(1)
          r     <- ZIO.asyncWithCompletionHandler[Integer](client.read(buffer, (), _))
        } yield (r, buffer.array.toList)

        val task = for {
          fiberServer  <- taskServer.fork
          fiberClient  <- taskClient.fork
          resultServer <- fiberServer.join
          resultClient <- fiberClient.join
          _            <- ZIO.succeed(server.close())
        } yield (resultServer, resultClient)

        assertZIO(task.exit)(
          succeeds[(Integer, (Integer, List[Byte]))](equalTo((Integer.valueOf(1), (Integer.valueOf(1), list))))
        )
      }
    ) @@ zioTag(future) @@ TestAspect.unix
  )
}

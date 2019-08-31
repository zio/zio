import collection.JavaConverters._
import scala.collection.mutable._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.Iterator

object ZStackServer {
  private def StringToByteBuffer(message: String): ByteBuffer = {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8))
  }
  private def ByteBufferToString(bytes: ByteBuffer): String = {
    return new String(bytes.array()).trim()
  }
  private def register(selector: Selector, serverSocket: ServerSocketChannel) = {
    val client: SocketChannel = serverSocket.accept()
    client.configureBlocking(false)
    client.register(selector, SelectionKey.OP_READ)
  }

  def testResponseEcho(buffer: ByteBuffer, key: SelectionKey) = {
    val client: SocketChannel = key.channel().asInstanceOf[SocketChannel]
    client.read(buffer)
    val received: String = ByteBufferToString(buffer)
    println(s"Server received: $received")
    if (received == "STOP_SERVER") {
      client.close()
    }
    buffer.flip
    client.write(StringToByteBuffer(s"Server received: $received"))
    buffer.clear
  }

  def apply(): Unit = {
    val selector: Selector = Selector.open()
    val zStackSocket: ServerSocketChannel = ServerSocketChannel.open()
    val zStackAddress: InetSocketAddress = new InetSocketAddress("localhost", 1111)
    zStackSocket.bind(zStackAddress)
    zStackSocket.configureBlocking(false)
    zStackSocket.register(selector, SelectionKey.OP_ACCEPT)
    val buffer: ByteBuffer = ByteBuffer.allocate(256)

    while (true) {
      selector.select()
      val zStackKeys: Set[SelectionKey] = selector.selectedKeys.asScala
      val zStackIter: Iterator[SelectionKey] = zStackKeys.iterator.asJava
      while (zStackIter.hasNext) {
        val currentKey: SelectionKey = zStackIter.next
        if (currentKey.isAcceptable) {
          register(selector, zStackSocket)
        } 
        if (currentKey.isReadable) {
          testResponseEcho(buffer, currentKey)
        }
        zStackIter.remove()
      }
    }
  }
}

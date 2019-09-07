package zio.zstack

import collection.JavaConverters._
import scala.collection.mutable._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Iterator

object ZStackServer {
  private def register(selector: Selector, serverSocket: ServerSocketChannel) = {
    val client: SocketChannel = serverSocket.accept()
    client.configureBlocking(false)
    client.register(selector, SelectionKey.OP_READ)
  }

  def responseReceived(buffer: ByteBuffer, key: SelectionKey, debug: Boolean): Boolean = {
    val client: SocketChannel = key.channel().asInstanceOf[SocketChannel]
    client.read(buffer)
    val received: String = ZStackCommands.ByteBufferToString(buffer)
    if (debug)
      println(s"Server received: $received")
    if (received == "STOP_SERVER") {
      println("closing client channel")
      client.close()
      return false
    }
    buffer.flip
    client.write(ZStackCommands.StringToByteBuffer(s"Server received: $received"))
    buffer.clear
    return true
  }

  def apply(config: ZStackConfig, selector: Selector, zStackSocket: ServerSocketChannel, zStackAddress: InetSocketAddress): Unit = {
    zStackSocket.socket.setReuseAddress(true)
    zStackSocket.bind(zStackAddress)
    zStackSocket.configureBlocking(false)
    zStackSocket.register(selector, SelectionKey.OP_ACCEPT)
    val buffer: ByteBuffer = ByteBuffer.allocate(256)

    var state: Boolean = true
    while (state) {
      selector.select()
      val zStackKeys: Set[SelectionKey] = selector.selectedKeys.asScala
      val zStackIter: Iterator[SelectionKey] = zStackKeys.iterator.asJava
      while (zStackIter.hasNext) {
        val currentKey: SelectionKey = zStackIter.next
        if (currentKey.isAcceptable) {
          register(selector, zStackSocket)
        } 
        if (currentKey.isReadable) {
          state = responseReceived(buffer, currentKey, config.debug)
          if (state == false) {
            println("Closing socket")
            zStackSocket.close()
            selector.close()
          }
        }
        zStackIter.remove()
      }
    } 
  }
}

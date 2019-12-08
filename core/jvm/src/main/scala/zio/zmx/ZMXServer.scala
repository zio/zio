/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.zmx

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ SelectionKey, Selector, ServerSocketChannel, SocketChannel }
import java.util.Iterator

import scala.collection.JavaConverters._
import scala.collection.mutable.Set

object ZMXServer {
  val BUFFER_SIZE = 256

  private def register(selector: Selector, serverSocket: ServerSocketChannel): SelectionKey = {
    val client: SocketChannel = serverSocket.accept()
    client.configureBlocking(false)
    client.register(selector, SelectionKey.OP_READ)
  }

  final val getCommand: PartialFunction[ZMXServerRequest, ZMXCommands] = {
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("dump") => ZMXCommands.FiberDump
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("test") => ZMXCommands.Test
    case ZMXServerRequest(command, None) if command.equalsIgnoreCase("stop") => ZMXCommands.Stop
  }

  private def handleCommand(command: ZMXCommands): ZMXMessage =
    command match {
      case ZMXCommands.FiberDump => ??? // use Fiber.dump
      case ZMXCommands.Metrics   => ??? // wip by @dkarlinsky
      case ZMXCommands.Test      => ZMXMessage("This is a TEST")
      case _                     => ZMXMessage("Unknown Command")
    }

  private def processCommand(received: String): Option[ZMXCommands] = {
    val request: Option[ZMXServerRequest] = ZMXProtocol.serverReceived(received)
    request.map(getCommand(_))
  }

  private def responseReceived(buffer: ByteBuffer, key: SelectionKey, debug: Boolean): Boolean = {
    val client: SocketChannel = key.channel().asInstanceOf[SocketChannel]
    client.read(buffer)
    val received: String = ZMXCommands.ByteBufferToString(buffer)
    if (debug)
      println(s"Server received: $received")
    val receivedCommand = processCommand(received)
    buffer.flip
    receivedCommand match {
      case Some(comm) if comm == ZMXCommands.Stop =>
        client.write(ZMXCommands.StringToByteBuffer(ZMXProtocol.generateReply(ZMXMessage("Stopping Server"), Success)))
        client.close()
        return false
      case Some(comm) =>
        val responseToSend: ZMXMessage = handleCommand(comm)
        client.write(ZMXCommands.StringToByteBuffer(ZMXProtocol.generateReply(responseToSend, Success)))
      case None =>
        client.write(ZMXCommands.StringToByteBuffer(ZMXProtocol.generateReply(ZMXMessage("No Response"), Fail)))
    }
    buffer.clear
    true
  }

  def apply(config: ZMXConfig): Unit = {
    val selector: Selector             = Selector.open()
    val zmxSocket: ServerSocketChannel = ServerSocketChannel.open()
    val zmxAddress: InetSocketAddress  = new InetSocketAddress(config.host, config.port)
    zmxSocket.socket.setReuseAddress(true)
    zmxSocket.bind(zmxAddress)
    zmxSocket.configureBlocking(false)
    zmxSocket.register(selector, SelectionKey.OP_ACCEPT)
    val buffer: ByteBuffer = ByteBuffer.allocate(BUFFER_SIZE)

    var state: Boolean = true
    while (state) {
      selector.select()
      val zmxKeys: Set[SelectionKey]      = selector.selectedKeys.asScala
      val zmxIter: Iterator[SelectionKey] = zmxKeys.iterator.asJava
      while (zmxIter.hasNext) {
        val currentKey: SelectionKey = zmxIter.next
        if (currentKey.isAcceptable) {
          register(selector, zmxSocket)
        }
        if (currentKey.isReadable) {
          state = responseReceived(buffer, currentKey, config.debug)
          if (!state) {
            zmxSocket.close()
            selector.close()
          }
        }
        zmxIter.remove()
      }
    }
  }
}

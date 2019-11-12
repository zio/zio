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

import collection.JavaConverters._
import scala.collection.mutable._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Iterator

object ZMXServer {
  private def register(selector: Selector, serverSocket: ServerSocketChannel) = {
    val client: SocketChannel = serverSocket.accept()
    client.configureBlocking(false)
    client.register(selector, SelectionKey.OP_READ)
  }

  final val getFiberDumpCommand: PartialFunction[ZMXServerRequest, ZMXCommands] = {
      case ZMXServerRequest(command, args) if command.equalsIgnoreCase("dump") => ZMXCommands.FiberDump
  }

  private def handleCommand(command: ZMXCommands): String = {
    command match {
      case ZMXCommands.FiberDump => ???
      case _ => ""
    }
  }

  private def processCommand(received: String): Option[ZMXCommands] = {
    val request: Option[ZMXServerRequest] = ZMXProtocol.serverReceived(received)
    request.map(getFiberDumpCommand(_))
  }

  private def responseReceived(buffer: ByteBuffer, key: SelectionKey, debug: Boolean): Boolean = {
    val client: SocketChannel = key.channel().asInstanceOf[SocketChannel]
    client.read(buffer)
    val received: String = ZMXCommands.ByteBufferToString(buffer)
    if (debug)
      println(s"Server received: $received")
    if (received == "STOP_SERVER") {
      println("closing client channel")
      client.close()
      return false
    }
    val receivedCommand = processCommand(received)
    buffer.flip
    receivedCommand match {
      case Some(comm) => {
      val responseToSend: String = handleCommand(comm)
      client.write(ZMXCommands.StringToByteBuffer(ZMXProtocol.generateReply(responseToSend, Success)))
      }
      case None => 
        client.write(ZMXCommands.StringToByteBuffer(ZMXProtocol.generateReply("No Response", Fail)))
    }
    buffer.clear
    true
  }

  def apply(config: ZMXConfig): Unit = {
    val selector: Selector = Selector.open()
    val zmxSocket: ServerSocketChannel = ServerSocketChannel.open()
    val zmxAddress: InetSocketAddress = new InetSocketAddress(config.host, config.port)
    zmxSocket.socket.setReuseAddress(true)
    zmxSocket.bind(zmxAddress)
    zmxSocket.configureBlocking(false)
    zmxSocket.register(selector, SelectionKey.OP_ACCEPT)
    val buffer: ByteBuffer = ByteBuffer.allocate(256)

    var state: Boolean = true
    while (state) {
      selector.select()
      val zmxKeys: Set[SelectionKey] = selector.selectedKeys.asScala
      val zmxIter: Iterator[SelectionKey] = zmxKeys.iterator.asJava
      while (zmxIter.hasNext) {
        val currentKey: SelectionKey = zmxIter.next
        if (currentKey.isAcceptable) {
          register(selector, zmxSocket)
        } 
        if (currentKey.isReadable) {
          state = responseReceived(buffer, currentKey, config.debug)
          if (state == false) {
            println("Closing socket")
            zmxSocket.close()
            selector.close()
          }
        }
        zmxIter.remove()
      }
    } 
  }
}

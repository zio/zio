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
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

class ZMXClient(config: ZMXConfig) {
  val buffer: ByteBuffer = ByteBuffer.allocate(256)

  def sendCommand(args: List[String]): String = {
    val sending: String = ZMXProtocol.generateRespCommand(args)
    sendMessage(sending)
  }

  def sendMessage(message: String): String = {
    val client: SocketChannel = SocketChannel.open(new InetSocketAddress(config.host, config.port))
    client.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)))
    client.read(buffer)
    val response: String = ZMXProtocol.ByteBufferToString(buffer)
    println(s"Response: ${response}")
    buffer.clear()
    response
  }
}

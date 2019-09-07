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

package zio.zstack

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.SocketChannel

object ZStackClient {
  val client: SocketChannel = SocketChannel.open(new InetSocketAddress("localhost", 1111))
  val buffer: ByteBuffer = ByteBuffer.allocate(256)

  private def StringToByteBuffer(message: String): ByteBuffer = {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8))
  }
  private def ByteBufferToString(bytes: ByteBuffer): String = {
    return new String(bytes.array()).trim()
    // return Charset.forName("UTF-8").decode(bytes).toString()
  }
  def SendMessage(message: String): String = {
    client.write(StringToByteBuffer(message))
    client.read(buffer)
    val response: String = ByteBufferToString(buffer)
    buffer.clear()
    println(s"Response from server: $response")
    return response
  }
}

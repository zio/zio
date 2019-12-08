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

import scala.scalajs.js
import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.net._
import scala.collection.mutable.ListBuffer

object ZMXServer {
  val sockets = ListBuffer[Socket]()
  private def responseReceived(buffer: Buffer): Boolean = {
    val received: String = buffer.toString()
    println(s"Server Received: $received")
    true
  }
  private def dataListener(buffer: Buffer) = {
    responseReceived(buffer)
    sockets.foreach(
      _.write("Received message thank you")
    )
  }
  private def closeListener(s: Socket, buffer: Buffer) = {
    val removeSocketIndex = sockets.indexWhere(x => x.remoteAddress == s.remoteAddress && x.remotePort == s.remotePort)
    sockets.remove(removeSocketIndex)
    println(s"Closed Socket: $s.remoteAddress : $s.remotePort")
  }
  private def connectionListener(s: Socket) = {
    println(s"Connected: $s.remoteAddress : $s.remotePort")
    sockets += s
    s.on("data", dataListener(_))
    s.on("close", closeListener(s, _))
  }
  def apply(config: ZMXConfig): Unit = {
    val server: Server = Net.createServer()
    server.listen(
      port = config.port,
      hostname = config.host,
      backlog = 128,
      callback = println(s"JS Server is running: port: $config.port host: $config.host").asInstanceOf[js.Function]
    )
    server.on("connection", connectionListener(_))
  }
}

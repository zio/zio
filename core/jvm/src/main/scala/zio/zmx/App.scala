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
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

object App {
  final def shutdown(selector: Selector, socket: ServerSocketChannel) = {
    socket.close()
    selector.close()
    println("zmx shutdown")
  }
  final def main(args: Array[String]): Unit = {
    val config = args.sliding(2, 1).toList.foldLeft(ZMXConfig.empty) { case (accumArgs, currArgs) => currArgs match {
      case Array("-h", host) => accumArgs.copy(host = host)
      case Array("--host", host) => accumArgs.copy(host = host)
      case Array("-p", port) => accumArgs.copy(port = port.toInt)
      case Array("--port", port) => accumArgs.copy(port = port.toInt)
      case Array("-d", debug) => accumArgs.copy(debug = debug.toBoolean)
      case Array("--debug", debug) => accumArgs.copy(debug = debug.toBoolean)
      case _ => accumArgs
      }
    }
    val selector: Selector = Selector.open()
    val socket: ServerSocketChannel = ServerSocketChannel.open()
    val address: InetSocketAddress = new InetSocketAddress(config.host, config.port)
    sys.addShutdownHook(shutdown(selector, socket))
    println(s"Starting zmx server on host: [${config.host}] port: [${config.port}]")
    ZMXServer(config, selector, socket, address)
 }
}

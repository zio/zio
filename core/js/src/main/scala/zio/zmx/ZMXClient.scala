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

object ZMXClient {
  val client: Socket = new Socket()

  private def responseListener(buffer: Buffer) = {
    val response: String = buffer.toString()
    println(s"Server Response was: $response")
  }

  def SendMessage(message: String): Boolean =
    client.write(Buffer.from(message))
  client.connect(
    1111,
    "localhost",
    SendMessage(_)
  )
  client.on("data", responseListener(_))
  client.on("close", println("Closed Client connection").asInstanceOf[js.Function])
}

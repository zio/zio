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

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

sealed trait ZMXCommands

object ZMXCommands {
  def ByteBufferToString(bytes: ByteBuffer): String = {
    return new String(bytes.array()).trim()
  }

  def StringToByteBuffer(message: String): ByteBuffer = {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8))
  }

  case object Test extends ZMXCommands
  case object Stop extends ZMXCommands
  case object Metrics extends ZMXCommands
  case object FiberDump extends ZMXCommands
}


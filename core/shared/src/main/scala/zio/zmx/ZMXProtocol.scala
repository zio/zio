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
import java.nio.channels.{SelectionKey, SocketChannel}
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets._

import zio.UIO

import scala.annotation.tailrec

object ZMXProtocol {

  /**
   *  Implementation of the RESP protocol to be used by ZMX for client-server communication
   *
   *  RESP Protocol Specs: https://redis.io/topics/protocol
   *
   */
  /** Response types */
  val MULTI = "*"
  val PASS  = "+"
  val FAIL  = "-"
  val BULK  = "$"

  /**
   * Generate message to send to server
   *
   *
   */
  def generateRespCommand(args: List[String]): String = {
    val protocol = new StringBuilder().append("*").append(args.length).append("\r\n")

    args.foreach { arg =>
      val length = arg.getBytes(UTF_8).length
      protocol.append("$").append(length).append("\r\n").append(arg).append("\r\n")
    }

    protocol.result
  }

  /**
   * Generate reply to send back to client
   *
   *
   */
  def generateReply(message: ZMXMessage, replyType: ZMXServerResponse): UIO[ByteBuffer] = {
    val reply: String = replyType match {
      case Success => s"+${message}"
      case Fail => s"-${message}"
    }
    println(s"reply: ${reply}")
    UIO.succeed(ByteBuffer.wrap(reply.getBytes(StandardCharsets.UTF_8)))
  }

  final val getSuccessfulResponse: PartialFunction[String, String] = {
    case s: String if s startsWith PASS => s.slice(1, s.length)
  }

  final val getErrorResponse: PartialFunction[String, String] = {
    case s: String if s startsWith FAIL => s.slice(1, s.length)
  }

  final val numberOfBulkStrings: PartialFunction[String, Int] = {
    case s: String if s startsWith MULTI => s.slice(1, s.length).toInt
  }

  final val sizeOfBulkString: PartialFunction[String, Int] = {
    case s: String if s startsWith BULK => s.slice(1, s.length).toInt
  }

  final val getBulkString: PartialFunction[(List[String], Int), String] = {
    case (s, d) if s.nonEmpty && d > 0 && s(1).length == d => s(1)
  }

  @tailrec
  final def getArgs(received: List[String], acc: List[String] = List()): List[String] =
    if (received.size > 1 && (received.head startsWith BULK)) {
      val result: String = getBulkString((received.slice(0, 2), sizeOfBulkString(received.head)))
      getArgs(received.slice(2, received.size), acc :+ result)
    } else
      acc

  /**
   * Extracts command and arguments received from client
   *
   * 1) Check how many bulk strings we received
   * 2) Extract each bulk string
   * 3) First bulk string is the command
   * 4) Subsequent bulk strings are the arguments to the command
   *
   * Sample: "*2\\r\\n\$3\\r\\nfoo\\r\\n\$3\\r\\nbar\\r\\n"
   *
   */
  def serverReceived(received: String): Option[ZMXServerRequest] = {
    val receivedList: List[String] = received.split("\r\n").toList
    val command: String = getBulkString((receivedList.slice(1, 3), sizeOfBulkString(receivedList(1))))
    if (receivedList.length < 4)
      Some(
        ZMXServerRequest(
          command = command,
          args = None
        )
      )
    else
      Some(
        ZMXServerRequest(
          command = command,
          args = Some(getArgs(receivedList.slice(3, receivedList.size)))
        )
      )
  }

  /**
   * Extract response received by client
   *
   * Success of format: +<message>
   * Error of format: -<message>
   *
   */
  val clientReceived: PartialFunction[String, String] = getSuccessfulResponse orElse getErrorResponse

  def StringToByteBuffer(message: UIO[String]): UIO[ByteBuffer] =
    for {
      content <- message
    } yield ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8))

  def ByteBufferToString(bytes: ByteBuffer): String =
    new String(bytes.array()).trim()

  def writeToClient(buffer: ByteBuffer, key: SelectionKey, message: ByteBuffer): ByteBuffer = {
    val client: SocketChannel = key.channel().asInstanceOf[SocketChannel]
    client.read(buffer)
    buffer.flip
    client.write(message)
    buffer.clear
    message
  }
}

package zio.zstack

import java.nio.charset.StandardCharsets._
import scala.annotation.tailrec

object ZStackProtocol {
  /** Response types */
  val MULTI   = "*"
  val PASS    = "+"
  val FAIL    = "-"
  val BULK    = "$"

  /**
   * Generate message to send to server 
   *
   *
   */ 
  def generateRespCommand(args: List[String]): String  = {
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
  def generateReply(message: String, replyType: ZStackServerResponse): String = {
    replyType match {
      case Success => s"+$message"
      case Fail => s"-$message"
    }
  }

  final val getSuccessfulResponse: PartialFunction[String, String] = {
    case s: String if s startsWith PASS => s.slice(1, s.size)
  }

  final val getErrorResponse: PartialFunction[String, String] = {
    case s: String if s startsWith FAIL => s.slice(1, s.size)
  }

  final val numberOfBulkStrings: PartialFunction[String, Int] = {
    case s: String if s startsWith MULTI => s.slice(1, s.size).toInt
  }

  final val sizeOfBulkString: PartialFunction[String, Int] = {
    case s: String if s startsWith BULK => s.slice(1, s.size).toInt
  }

  final val getBulkString: PartialFunction[(List[String], Int), String] = {
    case (s, d) if s.size > 0 && d > 0 && s(1).size == d => s(1)
  }

  @tailrec
  final def getArgs(received: List[String], acc: List[String] = List()): List[String] = {
    if (received.size > 1 && (received.head startsWith BULK)) {
      val result: String = getBulkString((received.slice(0,2), sizeOfBulkString(received(0))))
      getArgs(received.slice(2, received.size), acc :+ result)
    }
    else
      return acc
  }

  /**
   * Extracts command and arguments received from client
   *
   * 1) Check how many bulk strings we received
   * 2) Extract each bulk string
   * 3) First bulk string is the command
   * 4) Subsequent bulk strings are the arguments to the command
   *
   * Sample: "*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"
   *
   */
  def serverReceived(received: String): Option[ZStackServerRequest] = {
    val receivedList: List[String] = received.split("\r\n").toList
    val receivedCount: Int = numberOfBulkStrings(receivedList(0))
    if (receivedList.size < 1 || receivedCount < 1)
      return None
    val command: String = getBulkString((receivedList.slice(1,3), sizeOfBulkString(receivedList(1))))
    if (receivedList.size < 4)
      Some(ZStackServerRequest(
        command = command,
        args = None
        )
      )
    else
      Some(ZStackServerRequest(
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
  val clientReceived = getSuccessfulResponse orElse getErrorResponse
}


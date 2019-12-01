package zio.zmx

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object ZMXProtocolSpec extends ZIOBaseSpec {
  def spec = suite("ZMXProtocolSpec")(suite("Using the RESP protocol")(
    test("zmx test generating a successful command") {
      val p = ZMXProtocol.generateRespCommand(args = List("foobar"))
      assert (p, equalTo("*1\r\n$6\r\nfoobar\r\n"))
    },
    test("zmx test generating a successful multiple command") {
      val p = ZMXProtocol.generateRespCommand(args = List("foo", "bar"))
      assert(p, equalTo("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"))
    },
    test("zmx test generating a successful empty command") {
      val p = ZMXProtocol.generateRespCommand(args = List())
      assert(p, equalTo("*0\r\n"))
    },
    test("zmx test generating a success reply") {
      val p = ZMXProtocol.generateReply(ZMXMessage("foobar"), Success)
      assert(p, equalTo("+foobar"))
    },
    test("zmx test generating a fail reply") {
      val p = ZMXProtocol.generateReply(ZMXMessage("foobar"), Fail)
      assert(p, equalTo("-foobar"))
    },
    test("zmx get size of bulk string") {
      assert(ZMXProtocol.sizeOfBulkString("$6"), equalTo(6))
    },
    test("zmx get bulk string successfully") {
      assert(ZMXProtocol.getBulkString((List("$6", "foobar"), 6)), equalTo("foobar"))
    },
    test("zmx get the number of bulk strings") {
      assert(ZMXProtocol.numberOfBulkStrings("*6"), equalTo(6))
    },
    test("zmx get successful reponse") {
      assert(ZMXProtocol.getSuccessfulResponse("+foobar"), equalTo("foobar"))
    },
    test("zmx get error response") {
      assert(ZMXProtocol.getErrorResponse("-foobar"), equalTo("foobar"))
    },
    test("zmx get args from a list") {
      assert(ZMXProtocol.getArgs(List("$3", "foo", "$3", "bar" )), equalTo(List("foo", "bar")))
    },
    test("zmx server received a command with no args") {
      val expected = ZMXServerRequest("foobar", None)
      assert(ZMXProtocol.serverReceived("*1\r\n$6\r\nfoobar\r\n"), equalTo(Some(expected)))
    },
    test("zmx server received nothing") {
      assert(ZMXProtocol.serverReceived("*0\r\n"), equalTo(None))
    },
    test("zmx server received a command with one argument") {
      val expected = ZMXServerRequest("foobar", Some(List("argy")))
      assert(ZMXProtocol.serverReceived("*1\r\n$6\r\nfoobar\r\n$4\r\nargy\r\n"), equalTo(Some(expected)))
    }
  ))
}

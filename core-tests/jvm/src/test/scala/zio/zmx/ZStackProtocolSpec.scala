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

// import org.specs2.execute.Result
// import org.specs2.matcher.{ Expectable, Matcher }
import org.specs2.mutable


// class ZMXProtocolSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
class ZMXProtocolSpec extends mutable.Specification {
      "zmx test generating a successful command" >> testGenerateSuccessfulCommand
      "zmx test generating a successful multiple command" >> testGenerateSuccessfulMultiCommand
      "zmx test generating a successful empty command" >> testGenerateSuccessfulEmptyCommand
      "zmx test generating a success reply" >> testGenerateSuccessReply
      "zmx test generating a fail reply" >> testGenerateFailReply
      "zmx get size of bulk string" >> testSizeOfBulkString
      "zmx get bulk string successfully" >> testGetBulkString
      "zmx get the number of bulk strings" >> testNumberOfBulkStrings
      "zmx get successful reponse" >> testGetSuccessfulResponse
      "zmx get error response" >> testGetErrorResponse
      "zmx get args from a list" >> testGetArgs
      "zmx server received a command with no args" >> testServerReceivedCommandOnly
      "zmx server received nothing" >> testServerReceivedNothing
      "zmx server received a command with one argument" >> testServerReceivedCommandWithArg

      def testServerReceivedCommandWithArg = {
        val expected = ZMXServerRequest("foobar", Some(List("argy")))
        ZMXProtocol.serverReceived("*1\r\n$6\r\nfoobar\r\n$4\r\nargy\r\n") must_=== Some(expected)
      }

      def testServerReceivedNothing = {
        ZMXProtocol.serverReceived("*0\r\n") must_=== None
      }

      def testServerReceivedCommandOnly = {
        val expected = ZMXServerRequest("foobar", None)
        ZMXProtocol.serverReceived("*1\r\n$6\r\nfoobar\r\n") must_=== Some(expected)
      }

      def testGetArgs = {
        ZMXProtocol.getArgs(List("$3", "foo", "$3", "bar" )) must_=== List("foo", "bar")
      }

      def testGenerateSuccessfulCommand = {
        ZMXProtocol.generateRespCommand(args = List("foobar")) must_=== "*1\r\n$6\r\nfoobar\r\n"
      }

      def testGenerateSuccessfulMultiCommand = {
        ZMXProtocol.generateRespCommand(args = List("foo", "bar")) must_=== "*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"
      }

      def testGenerateSuccessfulEmptyCommand = {
        ZMXProtocol.generateRespCommand(args = List()) must_=== "*0\r\n"

      }

      def testGenerateSuccessReply = {
        ZMXProtocol.generateReply("foobar", Success) must_=== "+foobar"
      }

      def testGenerateFailReply = {
        ZMXProtocol.generateReply("foobar", Fail) must_=== "-foobar"
      }

      def testSizeOfBulkString = {
        ZMXProtocol.sizeOfBulkString("$6") must_=== 6
      }

      def testGetBulkString = {
        ZMXProtocol.getBulkString((List("$6", "foobar"), 6)) must_=== "foobar"
      }

      def testNumberOfBulkStrings = {
        ZMXProtocol.numberOfBulkStrings("*6") must_=== 6
      }

      def testGetSuccessfulResponse = {
        ZMXProtocol.getSuccessfulResponse("+foobar") must_=== "foobar"
      }

      def testGetErrorResponse = {
        ZMXProtocol.getErrorResponse("-foobar") must_=== "foobar"
      }

    }

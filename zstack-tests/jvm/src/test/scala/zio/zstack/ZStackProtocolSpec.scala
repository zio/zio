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

// import org.specs2.execute.Result
// import org.specs2.matcher.{ Expectable, Matcher }
import org.specs2.mutable


// class ZStackProtocolSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
class ZStackProtocolSpec extends mutable.Specification {
      "zstack test generating a successful command" >> testGenerateSuccessfulCommand
      "zstack test generating a successful multiple command" >> testGenerateSuccessfulMultiCommand
      "zstack test generating a successful empty command" >> testGenerateSuccessfulEmptyCommand
      "zstack test generating a success reply" >> testGenerateSuccessReply
      "zstack test generating a fail reply" >> testGenerateFailReply
      "zstack get size of bulk string" >> testSizeOfBulkString
      "zstack get bulk string successfully" >> testGetBulkString
      "zstack get the number of bulk strings" >> testNumberOfBulkStrings
      "zstack get successful reponse" >> testGetSuccessfulResponse
      "zstack get error response" >> testGetErrorResponse
      "zstack get args from a list" >> testGetArgs
      "zstack server received a command with no args" >> testServerReceivedCommandOnly
      "zstack server received nothing" >> testServerReceivedNothing
      "zstack server received a command with one argument" >> testServerReceivedCommandWithArg

      def testServerReceivedCommandWithArg = {
        val expected = ZStackServerRequest("foobar", Some(List("argy")))
        ZStackProtocol.serverReceived("*1\r\n$6\r\nfoobar\r\n$4\r\nargy\r\n") must_=== Some(expected)
      }

      def testServerReceivedNothing = {
        ZStackProtocol.serverReceived("*0\r\n") must_=== None
      }

      def testServerReceivedCommandOnly = {
        val expected = ZStackServerRequest("foobar", None)
        ZStackProtocol.serverReceived("*1\r\n$6\r\nfoobar\r\n") must_=== Some(expected)
      }

      def testGetArgs = {
        ZStackProtocol.getArgs(List("$3", "foo", "$3", "bar" )) must_=== List("foo", "bar")
      }

      def testGenerateSuccessfulCommand = {
        ZStackProtocol.generateRespCommand(args = List("foobar")) must_=== "*1\r\n$6\r\nfoobar\r\n"
      }

      def testGenerateSuccessfulMultiCommand = {
        ZStackProtocol.generateRespCommand(args = List("foo", "bar")) must_=== "*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"
      }

      def testGenerateSuccessfulEmptyCommand = {
        ZStackProtocol.generateRespCommand(args = List()) must_=== "*0\r\n"

      }

      def testGenerateSuccessReply = {
        ZStackProtocol.generateReply("foobar", Success) must_=== "+foobar"
      }

      def testGenerateFailReply = {
        ZStackProtocol.generateReply("foobar", Fail) must_=== "-foobar"
      }

      def testSizeOfBulkString = {
        ZStackProtocol.sizeOfBulkString("$6") must_=== 6
      }

      def testGetBulkString = {
        ZStackProtocol.getBulkString((List("$6", "foobar"), 6)) must_=== "foobar"
      }

      def testNumberOfBulkStrings = {
        ZStackProtocol.numberOfBulkStrings("*6") must_=== 6
      }

      def testGetSuccessfulResponse = {
        ZStackProtocol.getSuccessfulResponse("+foobar") must_=== "foobar"
      }

      def testGetErrorResponse = {
        ZStackProtocol.getErrorResponse("-foobar") must_=== "foobar"
      }

    }

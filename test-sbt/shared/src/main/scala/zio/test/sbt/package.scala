package zio.test

import zio.{ FunctionIO, UIO }

package object sbt {
  type SendSummary = FunctionIO[Nothing, Summary, Unit]
  object SendSummary {
    def fromSend(send: Summary => Unit): SendSummary = FunctionIO.effectTotal(send)

    def fromSendM(send: Summary => UIO[Unit]): SendSummary = FunctionIO.fromFunctionM(send)

    def noop: SendSummary = FunctionIO.succeed(())
  }

}

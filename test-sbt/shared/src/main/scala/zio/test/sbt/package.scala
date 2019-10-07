package zio.test
import zio.{ FunctionIO, UIO }

package object sbt {
  type SendSummary = FunctionIO[Nothing, String, Unit]
  object SendSummary {
    def fromSend(send: String => Unit): SendSummary = FunctionIO.effectTotal(send)

    def fromSendM(send: String => UIO[Unit]): SendSummary = FunctionIO.fromFunctionM(send)

    def noop: SendSummary = FunctionIO.succeed(())
  }
}

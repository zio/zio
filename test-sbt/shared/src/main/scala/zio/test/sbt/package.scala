package zio.test

import zio.{ FunctionIO, UIO }

package object sbt {

  type SendSummary = FunctionIO[Nothing, Summary, Unit]

  object SendSummary {
    def fromSend(send: Summary => Unit): SendSummary = FunctionIO.effectTotal(send)

    def fromSendM(send: Summary => UIO[Unit]): SendSummary = FunctionIO.fromFunctionM(send)

    def noop: SendSummary = FunctionIO.succeed(())
  }

  /**
   * Inserts the ANSI escape code for the current color at the beginning of
   * each line of the specified string so the string will be displayed with the
   * correct color by the `SBTTestLogger`.
   */
  private[sbt] def colored(s: String): String =
    Render.render.run(s).map { case (_, a) => Render.colored(a).mkString }.toOption.getOrElse("")
}

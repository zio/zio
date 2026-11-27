package zio.test

/**
 * A line that `TestConsole` read with `readLine` (`Input`) or wrote with
 * `printLine` (`Output`), recorded in the `TestAnnotation.output` annotation.
 */
private[test] sealed trait ConsoleIO
private[test] object ConsoleIO {
  case class Input(line: String)  extends ConsoleIO
  case class Output(line: String) extends ConsoleIO
}

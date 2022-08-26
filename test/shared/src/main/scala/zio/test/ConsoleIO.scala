package zio.test

sealed trait ConsoleIO

case class ConsoleInput(line: String) extends ConsoleIO
case class ConsoleOutput(line: String) extends ConsoleIO

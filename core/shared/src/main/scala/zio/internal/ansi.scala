package zio.internal

object ansi {
  trait AnsiCode {
    def code: String
  }

  val Reset = "\u001b[0m"

  sealed abstract class Color(val code: String) extends AnsiCode

  object Color {
    case object Black   extends Color("\u001b[30m")
    case object Blue    extends Color("\u001b[34m")
    case object Cyan    extends Color("\u001b[36m")
    case object Green   extends Color("\u001b[32m")
    case object Magenta extends Color("\u001b[35m")
    case object Red     extends Color("\u001b[31m")
    case object White   extends Color("\u001b[97m")
    case object Yellow  extends Color("\u001b[33m")
  }

  sealed abstract class Style(val code: String) extends AnsiCode

  object Style {
    case object Bold       extends Style("\u001b[1m")
    case object Faint      extends Style("\u001b[2m")
    case object Underlined extends Style("\u001b[4m")
  }

  implicit class AnsiStringOps(private val self: String) extends AnyVal {
    // Colors
    def black: String   = using(Color.Black)
    def blue: String    = using(Color.Blue)
    def cyan: String    = using(Color.Cyan)
    def green: String   = using(Color.Green)
    def magenta: String = using(Color.Magenta)
    def red: String     = using(Color.Red)
    def white: String   = using(Color.White)
    def yellow: String  = using(Color.Yellow)

    // Styles
    def bold: String       = using(Style.Bold)
    def faint: String      = using(Style.Faint)
    def underlined: String = using(Style.Underlined)

    private def using(ansiCode: AnsiCode) = ansiCode.code + self + Reset
  }
}

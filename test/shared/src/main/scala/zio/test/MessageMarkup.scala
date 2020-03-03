package zio.test

import scala.io.AnsiColor

private[test] object MessageMarkup {
  case class Message(lines: Vector[Line] = Vector.empty) {
    def :+(line: Line)                          = Message(lines :+ line)
    def ++(message: Message)                    = Message(lines ++ message.lines)
    def map(f: Line => Line)                    = copy(lines = lines.map(f))
    def splitOnLineBreaks                       = Message(lines.flatMap(_.splitOnLineBreaks))
    def withOffset(offset: Int)                 = Message(lines.map(_.withOffset(offset)))
    def replace(what: String, withWhat: String) = copy(lines = lines.map(_.replace(what, withWhat)))
  }
  object Message {
    def apply(lines: Seq[Line]): Message = Message(lines.toVector)
    def apply(lineText: String): Message = Fragment.of(lineText).toLine.toMessage
  }
  case class Line(fragments: Vector[Fragment] = Vector.empty, offset: Int = 0) {
    def :+(fragment: Fragment)    = Line(fragments :+ fragment, offset)
    def +(fragment: Fragment)     = Line(fragments :+ fragment, offset)
    def prepend(message: Message) = Message(this +: message.lines)
    def +(line: Line)             = Message(Vector(this, line))
    def ++(line: Line)            = copy(fragments = fragments ++ line.fragments)
    def withOffset(shift: Int)    = copy(offset = offset + shift)
    def toMessage                 = Message(Vector(this))

    def splitOnLineBreaks: Seq[Line] = {
      case class SubFragment(fragment: Fragment, endsWithLF: Boolean)
      def subFragment(ansiColor: String)(text: String) =
        SubFragment(Fragment.of(text.stripLineEnd, ansiColor), text.endsWith("\n") || text.endsWith("\r"))
      case class SubLine(line: Line, endsWithLF: Boolean) {
        def +(fragment: SubFragment) = SubLine(line + fragment.fragment, fragment.endsWithLF)
      }
      def subLine(fragment: SubFragment, offset: Int) =
        SubLine(Line(Vector(fragment.fragment), offset), fragment.endsWithLF)
      fragments
        .foldLeft(Vector.empty[SubLine]) {
          case (lines, fragment) =>
            val subFragments = fragment.text.linesWithSeparators.toVector.map(subFragment(fragment.ansiColorCode))
            if (lines.isEmpty) {
              subFragments.map(subLine(_, offset))
            } else {
              val beforeLast = lines.take(lines.size - 1)
              val last       = lines.last
              if (last.endsWithLF) lines ++ subFragments.map(subLine(_, offset))
              else (beforeLast :+ (last + subFragments.head)) ++ subFragments.tail.map(subLine(_, offset))
            }

        }
        .map(_.line)
    }

    def replace(what: String, withWhat: String) =
      copy(fragments = fragments.map(_.replace(what, withWhat)))
  }
  object Line {
    def fromString(text: String, offset: Int = 0): Line = Fragment.plain(text).toLine.withOffset(offset)
  }

  case class Fragment private (text: String, ansiColorCode: String) {
    def +:(line: Line)      = prepend(line)
    def prepend(line: Line) = Line(this +: line.fragments, line.offset)
    def +(f: Fragment)      = Line(Vector(this, f))
    def toLine              = Line(Vector(this))
    override def toString   = s"Fragment($render)";
    def render =
      if (ansiColorCode.nonEmpty) ansiColorCode + text + AnsiColor.RESET
      else text
    def replace(what: String, withWhat: String) = copy(text.replace(what, withWhat))
  }

  object Fragment {
    def red(s: String)    = of(s, AnsiColor.RED)
    def blue(s: String)   = of(s, AnsiColor.BLUE)
    def yellow(s: String) = of(s, AnsiColor.YELLOW)
    def green(s: String)  = of(s, AnsiColor.GREEN)
    def cyan(s: String)   = of(s, AnsiColor.CYAN)
    def plain(s: String)  = of(s)
    def of(text: String, ansiColorCode: String = "") =
      if (text.isEmpty) Fragment(text, "")
      else Fragment(text.replace("\u001b", "\\e"), ansiColorCode)
  }
}

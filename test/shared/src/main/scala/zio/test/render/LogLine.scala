/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.render

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.LogLine.Fragment.Style

/**
 * The building blocks of rendered test output: a [[LogLine.Message]] is a sequence of [[LogLine.Line]]s, and a line is a sequence of styled [[LogLine.Fragment]]s with an indentation offset.
 *
 * Renderers such as [[ConsoleRenderer]] turn these values into text.
 */
object LogLine {

  /** A sequence of lines of rendered output. */
  case class Message(lines: Vector[Line] = Vector.empty) {

    /**
     * Appends `fragment` to the end of the last line, keeping that line's offset. On an empty message, returns a message with one line that holds `fragment`.
     */
    def +(fragment: Fragment) =
      this +++ Message(Vector(Line(Vector(fragment))))

    /** Returns this message with `line` added as its first line. */
    def +:(line: Line) = Message(line +: lines)

    /**
     * Adds `fragment` at the start of the first line, not at the end of the message. The changed first line gets offset 0. On an empty message, returns a message with one line that holds `fragment`.
     */
    def :+(fragment: Fragment): Message = Message(lines match {
      case line +: lines => (fragment +: line) +: lines
      case _             => Vector(fragment.toLine)
    })

    /** Returns this message with `line` added as its last line. */
    def :+(line: Line) = Message(lines :+ line)

    /** Returns a message with the lines of this message followed by the lines of `message`. */
    def ++(message: Message) = Message(lines ++ message.lines)

    /**
     * Joins the two messages so that the last line of this message and the first line of `message` become one line, which keeps the offset of the line from this message. The other lines follow unchanged. When either message is empty, it is the same as `++`.
     */
    def +++(message: Message) =
      (lines.lastOption, message.lines.headOption) match {
        case (Some(last), Some(head)) =>
          Message(lines.dropRight(1) :+ (last ++ head)) ++ Message(message.lines.drop(1))
        case _ =>
          this ++ message
      }

    /** Returns this message without its first `n` lines. */
    def drop(n: Int): Message = Message(lines.drop(n))

    /** Returns a message with `f` applied to each line. */
    def map(f: Line => Line): Message = Message(lines = lines.map(f))

    /** Adds `offset` to the offset of each line. A negative `offset` lowers it. */
    def withOffset(offset: Int): Message = Message(lines.map(_.withOffset(offset)))

    /** Returns this message with `line` inserted between each pair of adjacent lines. */
    def intersperse(line: Line): Message = Message(
      lines.foldRight(List.empty[Line]) { case (ln, rest) =>
        ln :: (if (rest.isEmpty) Nil else line :: rest)
      }
    )
  }

  /** Constructors for [[LogLine.Message]]. */
  object Message {

    /** Creates a message from a sequence of lines. */
    def apply(lines: Seq[Line]): Message = Message(lines.toVector)

    /**
     * Creates a message with one line that holds `lineText` as a single fragment in the `Default` style. The text is not split at line breaks.
     */
    def apply(lineText: String): Message = Fragment(lineText).toLine.toMessage

    /** A message with no lines. */
    val empty: Message = Message()
  }

  /**
   * One line of rendered output: a sequence of styled fragments and an indentation `offset`.
   */
  case class Line(fragments: Vector[Fragment] = Vector.empty, offset: Int = 0) { self =>

    /** Returns a line with `fragment` added at the start. The result has offset 0. */
    def +:(fragment: Fragment): Line = Line(fragment +: fragments)

    /** Returns a line with `fragment` added at the end. The result has offset 0. */
    def :+(fragment: Fragment): Line = Line(fragments :+ fragment)

    /** Returns a line with `fragment` added at the end. The result has offset 0. */
    def +(fragment: Fragment): Line = Line(fragments :+ fragment)

    /** Returns `message` with this line added as its first line. */
    def prepend(message: Message): Message = Message(this +: message.lines)

    /** Returns a message with two lines: this line, then `line`. */
    def +(line: Line): Message = Message(Vector(this, line))

    /** Returns this line with the fragments of `line` appended, keeping the offset of this line. */
    def ++(line: Line): Line = copy(fragments = fragments ++ line.fragments)

    /** Adds `shift` to the offset of this line. A negative `shift` lowers it. */
    def withOffset(shift: Int): Line = copy(offset = offset + shift)

    /** Returns a message that holds only this line. */
    def toMessage: Message = Message(Vector(this))

    /**
     * Merges each run of adjacent fragments that have equal styles (compared with `==`) into one fragment, keeping the text and the offset. The wrapping styles `Bold`, `Underlined` and `Ansi` hold a fragment, so two bold fragments with different text are not merged.
     */
    def optimized: Line = copy(fragments = optimize(fragments))
    private def optimize(fragments: Vector[Fragment]) =
      fragments
        .foldRight(List.empty[Fragment]) { case (curr, rest) =>
          rest match {
            case next :: fs =>
              if (curr.style == next.style) curr.copy(text = curr.text + next.text) :: fs
              else curr :: next :: fs
            case Nil => curr :: Nil
          }
        }
        .toVector
  }

  /** Constructors for [[LogLine.Line]]. */
  object Line {

    /**
     * Creates a line with `text` as a single fragment in the `Default` style, at `offset`. The text is not split at line breaks.
     */
    def fromString(text: String, offset: Int = 0): Line = Fragment(text).toLine.withOffset(offset)

    /** A line with no fragments and offset 0. */
    val empty: Line = Line()
  }

  /** A piece of text with one [[LogLine.Fragment.Style]]. */
  case class Fragment(text: String, style: Style = Style.Default) { self =>

    /**
     * Returns `line` with this fragment added at its start, keeping the offset of `line`. As a right-associative operator it is written `line +: fragment`; `fragment +: line` calls `Line.+:` instead.
     */
    def +:(line: Line) = prepend(line)

    /** Returns `line` with this fragment added at its start, keeping the offset of `line`. */
    def prepend(line: Line): Line = Line(this +: line.fragments, line.offset)

    /** Returns a line, at offset 0, with this fragment followed by `f`. */
    def +(f: Fragment) = Line(Vector(this, f))

    /** Returns a line, at offset 0, that holds only this fragment. */
    def toLine: Line = Line(Vector(this))

    /** Returns this fragment with its text repeated `n` times. For `n` of 0 or less, the text is empty. */
    def *(n: Int) = copy(text = text * n)

    /** Returns this fragment in the `Bold` style, which wraps this fragment with its current style. */
    def bold: Fragment =
      copy(style = Style.Bold(self))

    /** Returns this fragment in the `Underlined` style, which wraps this fragment with its current style. */
    def underlined: Fragment =
      copy(style = Style.Underlined(self))

    /**
     * Returns this fragment in the `Ansi` style with `ansiColor`, which wraps this fragment with its current style.
     */
    def ansi(ansiColor: String): Fragment =
      copy(style = Style.Ansi(self, ansiColor))
  }

  /** The styles of [[LogLine.Fragment]]. */
  object Fragment {

    /**
     * The style of a [[LogLine.Fragment]]. A renderer decides how each style looks.
     */
    sealed trait Style

    /** The cases of [[LogLine.Fragment.Style]]. */
    object Style {

      /** The style of the `primary` helper. */
      case object Primary extends Style

      /** Plain text, the style of the `fr` helper and of fragments created without a style. */
      case object Default extends Style

      /** The style of the `warn` helper, used for ignored tests. */
      case object Warning extends Style

      /** The style of the `error` helper, used for failures. */
      case object Error extends Style

      /** The style of the `info` helper, used for passed tests and suites. */
      case object Info extends Style

      /** The style of the `detail` helper, used for the source location of a failure. */
      case object Detail extends Style

      /** The style of the `dim` helper. */
      case object Dimmed extends Style

      /** Bold text; `fr` is the fragment with the style it wraps. */
      final case class Bold(fr: Fragment) extends Style

      /** Underlined text; `fr` is the fragment with the style it wraps. */
      final case class Underlined(fr: Fragment) extends Style

      /** Text in the color given by `ansiColor`; `fr` is the fragment with the style it wraps. */
      final case class Ansi(fr: Fragment, ansiColor: String) extends Style
    }
  }
}

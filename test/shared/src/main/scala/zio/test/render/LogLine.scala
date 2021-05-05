/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.test.render.LogLine.Fragment.Style

object LogLine {
  case class Message(lines: Vector[Line] = Vector.empty) {
    def +:(line: Line)                   = Message(line +: lines)
    def :+(line: Line)                   = Message(lines :+ line)
    def ++(message: Message)             = Message(lines ++ message.lines)
    def drop(n: Int): Message            = Message(lines.drop(n))
    def map(f: Line => Line): Message    = Message(lines = lines.map(f))
    def withOffset(offset: Int): Message = Message(lines.map(_.withOffset(offset)))
    def intersperse(line: Line): Message = Message(
      lines.foldRight(List.empty[Line]) { case (ln, rest) =>
        ln :: (if (rest.isEmpty) Nil else line :: rest)
      }
    )
  }
  object Message {
    def apply(lines: Seq[Line]): Message = Message(lines.toVector)
    def apply(lineText: String): Message = Fragment(lineText).toLine.toMessage
    val empty: Message                   = Message()
  }
  case class Line(fragments: Vector[Fragment] = Vector.empty, offset: Int = 0) {
    def :+(fragment: Fragment)             = Line(fragments :+ fragment)
    def +(fragment: Fragment)              = Line(fragments :+ fragment)
    def prepend(message: Message): Message = Message(this +: message.lines)
    def +:(message: Message)               = prepend(message)
    def +(line: Line)                      = Message(Vector(this, line))
    def ++(line: Line)                     = copy(fragments = fragments ++ line.fragments)
    def withOffset(shift: Int): Line       = copy(offset = offset + shift)
    def toMessage: Message                 = Message(Vector(this))

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
  object Line {
    def fromString(text: String, offset: Int = 0): Line = Fragment(text).toLine.withOffset(offset)
    val empty: Line                                     = Line()
  }
  case class Fragment(text: String, style: Style = Style.Default) {
    def +:(line: Line)            = prepend(line)
    def prepend(line: Line): Line = Line(this +: line.fragments, line.offset)
    def +(f: Fragment)            = Line(Vector(this, f))
    def toLine: Line              = Line(Vector(this))
  }
  object Fragment {
    sealed trait Style
    object Style {
      case object Primary extends Style
      case object Default extends Style
      case object Warning extends Style
      case object Error   extends Style
      case object Info    extends Style
      case object Detail  extends Style
    }
  }
}

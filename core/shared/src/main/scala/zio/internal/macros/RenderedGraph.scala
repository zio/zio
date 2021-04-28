package zio.internal.macros

import zio.internal.ansi.{AnsiStringOps, Color}

private[macros] sealed trait RenderedGraph { self =>
  def ++(that: RenderedGraph): RenderedGraph
  def >>>(that: RenderedGraph): RenderedGraph
  def render(depth: Int): String
  def render: String = render(0)
}

private[macros] object RenderedGraph {
  def apply(string: String): RenderedGraph = Value(string)

  private val colors = List(Color.Blue, Color.Cyan, Color.Red, Color.Magenta, Color.Green)

  final case class Value(name: String, children: List[RenderedGraph] = List.empty) extends RenderedGraph { self =>
    override def ++(that: RenderedGraph): RenderedGraph = that match {
      case value: Value =>
        Row(List(self, value))
      case Row(values) =>
        Row(self +: values)
    }

    override def >>>(that: RenderedGraph): RenderedGraph =
      that match {
        case Value(string, children) => Value(string, self +: children)
        case Row(_)                  => throw new Error("NOT LIKE THIS")
      }

    override def render(depth: Int): String = {
      val node         = if (depth == 0) "◉" else "◑".faint
      val color        = colors(depth % colors.length)
      val displayTitle = s"$node ".withAnsi(color) + name.bold.withAnsi(color)

      val childCount = children.length
      var idx        = 0

      (displayTitle +:
        children.map { g =>
          idx += 1

          val isNested = idx < childCount

          val symbol = if (isNested) "├─" else "╰─"

          val lines = g.render(depth + 1).split("\n")

          val child = (lines.head +: lines.tail.map { line =>
            if (isNested)
              "│ ".faint.withAnsi(color) + line
            else
              "  " + line
          })
            .mkString("\n")

          symbol.faint.withAnsi(color) + child
        })
        .mkString("\n")

    }

  }

  final case class Row(values: List[RenderedGraph]) extends RenderedGraph { self =>
    override def ++(that: RenderedGraph): RenderedGraph =
      that match {
        case value: Value =>
          Row(self.values :+ value)
        case Row(values) =>
          Row(self.values ++ values)
      }

    override def >>>(that: RenderedGraph): RenderedGraph =
      that match {
        case Value(string, children) => Value(string, self.values ++ children)
        case Row(_)                  => throw new Error("NOT LIKE THIS")
      }

    override def render(depth: Int): String =
      values
        .map(_.render(depth))
        .mkString("\n\n")
  }
}

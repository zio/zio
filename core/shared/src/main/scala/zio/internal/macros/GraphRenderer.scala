package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio.internal.macros.StringUtils.StringOps

private[macros] sealed trait RenderGraph { self =>
  def ++(that: RenderGraph): RenderGraph
  def >>>(that: RenderGraph): RenderGraph
  def render: String
}

private[macros] object RenderGraph {
  def apply(string: String): RenderGraph = Value(string)

  final case class Value(string: String, children: List[RenderGraph] = List.empty) extends RenderGraph { self =>
    override def ++(that: RenderGraph): RenderGraph = that match {
      case value: Value =>
        Row(List(self, value))
      case Row(values) =>
        Row(self +: values)
    }

    override def >>>(that: RenderGraph): RenderGraph =
      that match {
        case Value(string, children) => Value(string, self +: children)
        case Row(_)                  => throw new Error("NOT LIKE THIS")
      }

    override def render: String = {
      val renderedChildren = children.map(_.render)
      val childCount       = children.length
      val connectors =
        renderedChildren
          .foldLeft((0, "")) { case ((idx, acc), child) =>
            val maxWidth  = child.maxLineWidth
            val half      = maxWidth / 2
            val remainder = maxWidth % 2

            val beginChar = if (idx == 0) " " else "─"
            val centerChar =
              if (idx == 0) "┌"
              else if (idx + 1 == childCount) "┐"
              else "┬"
            val endChar = if (idx + 1 == childCount) " " else "─"

            val addition = (beginChar * half) + centerChar + (endChar * (half - (1 - remainder)))
            val newStr   = acc + addition
            (idx + 1, newStr)
          }
          ._2

      val joinedChildren = renderedChildren.foldLeft("")(_ +++ _)
      val maxChildWidth  = joinedChildren.maxLineWidth

      val midpoint = maxChildWidth / 2
      val connectorsWithCenter =
        if (connectors.length > midpoint) {
          val char =
            if (childCount == 1) '│'
            else if (connectors(midpoint) == '─') '┴'
            else '┼'
          connectors.updated(midpoint, char)
        } else
          connectors

      val padding = Math.max(0, maxChildWidth - string.length - 1) / 2

      val centered = (" " * (padding + 1)) + string.white + (" " * (padding + 1))

      Seq(
        centered,
        connectorsWithCenter,
        joinedChildren
      ).mkString("\n")
    }

  }

  final case class Row(values: List[RenderGraph]) extends RenderGraph { self =>
    override def ++(that: RenderGraph): RenderGraph =
      that match {
        case value: Value =>
          Row(self.values :+ value)
        case Row(values) =>
          Row(self.values ++ values)
      }

    override def >>>(that: RenderGraph): RenderGraph =
      that match {
        case Value(string, children) => Value(string, self.values ++ children)
        case Row(_)                  => throw new Error("NOT LIKE THIS")
      }

    override def render: String = values.map(_.render).foldLeft("")(_ +++ _)
  }

  implicit val layerLike: LayerLike[RenderGraph] = new LayerLike[RenderGraph] {
    override def empty = Row(List.empty)

    override def composeH(lhs: RenderGraph, rhs: RenderGraph): RenderGraph = lhs ++ rhs

    override def composeV(lhs: RenderGraph, rhs: RenderGraph): RenderGraph = lhs >>> rhs
  }
}

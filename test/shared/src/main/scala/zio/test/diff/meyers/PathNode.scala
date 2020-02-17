package zio.test.diff.meyers

private[meyers] sealed abstract class PathNode(val i: Int, val j: Int, val prev: PathNode) {

  def isSnake: Boolean
  final def isBootstrap: Boolean =
    i < 0 || j < 0
  @scala.annotation.tailrec
  final def previousSnake: PathNode =
    if (isBootstrap) null
    else if (!isSnake && prev != null) prev.previousSnake
    else this

  override def toString: String = {
    val buf  = new StringBuffer("[")
    var node = this
    while (node != null) {
      buf.append("(")
      buf.append(Integer.toString(node.i))
      buf.append(",")
      buf.append(Integer.toString(node.j))
      buf.append(")")
      node = node.prev
    }
    buf.append("]")
    buf.toString
  }
}

private[meyers] final class DiffNode(i: Int, j: Int, prev: PathNode)
    extends PathNode(i, j, if (prev == null) null else prev.previousSnake) {
  override def isSnake: Boolean = false
}

private[meyers] final class Snake(i: Int, j: Int, prev: PathNode) extends PathNode(i, j, prev) {
  override def isSnake: Boolean = true
}

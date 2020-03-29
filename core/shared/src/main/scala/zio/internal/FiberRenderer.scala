package zio.internal

import zio.Fiber.Status.{ Done, Finishing, Running, Suspended }
import zio.{ Fiber, UIO, ZIO }

private[zio] object FiberRenderer {
  private def zipWithHasNext[A](it: Iterable[A]): Iterable[(A, Boolean)] =
    if (it.isEmpty)
      Seq.empty
    else {
      Iterable.concat(it.dropRight(1).map((_, true)), Seq((it.last, false)))
    }

  def prettyPrintM(dump: Fiber.Dump): UIO[String] = UIO {
    val time = System.currentTimeMillis()

    val millis  = (time - dump.fiberId.startTimeMillis)
    val seconds = millis / 1000L
    val minutes = seconds / 60L
    val hours   = minutes / 60L

    val name = dump.fiberName.fold("")(name => "\"" + name + "\" ")
    val lifeMsg = (if (hours == 0) "" else s"${hours}h") +
      (if (hours == 0 && minutes == 0) "" else s"${minutes}m") +
      (if (hours == 0 && minutes == 0 && seconds == 0) "" else s"${seconds}s") +
      (s"${millis}ms")
    val waitMsg = dump.status match {
      case Suspended(_, _, _, blockingOn, _) =>
        if (blockingOn.nonEmpty)
          "waiting on " + blockingOn.map(id => s"#${id.seqNumber}").mkString(", ")
        else ""
      case _ => ""
    }
    val statMsg = renderStatus(dump.status)

    s"""
       |${name}#${dump.fiberId.seqNumber} (${lifeMsg}) ${waitMsg}
       |   Status: ${statMsg}
       |${dump.trace.prettyPrint}
       |""".stripMargin
  }

  def renderStatus(status: Fiber.Status): String =
    status match {
      case Done         => "Done"
      case Finishing(b) => "Finishing(" + (if (b) "interrupting" else "") + ")"
      case Running(b)   => "Running(" + (if (b) "interrupting" else "") + ")"
      case Suspended(_, interruptible, epoch, _, asyncTrace) =>
        val in = if (interruptible) "interruptible" else "uninterruptible"
        val ep = s"${epoch} asyncs"
        val as = asyncTrace.map(_.prettyPrint).mkString(" ")
        s"Suspended(${in}, ${ep}, ${as})"
    }

  def renderHierarchy(trees: Iterable[Fiber.Tree]): String =
    zipWithHasNext(trees).map {
      case (tree, hasNext) => renderOne(tree, hasNext)
    }.mkString

  def renderOne(tree: Fiber.Tree, hasNextSibling: Boolean): String = {
    def go(t: Fiber.Tree, prefix: String, hasNext: Boolean): String = {
      val childrenResults = {
        val prefixUpdate = if (hasNext) "|   " else "    "
        val newPrefix    = prefix + prefixUpdate
        zipWithHasNext(t.children).map {
          case (c, nxt) => go(c, newPrefix, nxt)
        }
      }
      val nodeString = {
        val nameStr   = t.fiberName.fold("")(n => "\"" + n + "\" ")
        val statusMsg = renderStatus(t.status)
        s"${prefix}+---${nameStr}#${t.fiberId.seqNumber} Status: $statusMsg\n"
      }

      nodeString + childrenResults.mkString

    }

    go(tree, "", hasNextSibling)
  }

  def collectTree(f: Fiber.Runtime[_, _]): UIO[Fiber.Tree] =
    for {
      id              <- f.id
      name            <- f.getRef(Fiber.fiberName)
      status          <- f.status
      children        <- f.children
      childrenResults <- ZIO.foreach(children)(c => collectTree(c.asInstanceOf[Fiber.Runtime[_, _]]))
    } yield {
      Fiber.Tree(id, name, status, childrenResults)
    }

  def dumpStr(fibers: Seq[Fiber.Runtime[_, _]]): UIO[String] =
    for {
      trees      <- ZIO.foreach(fibers)(collectTree)
      treeString = renderHierarchy(trees)
      dumps      <- Fiber.dump(fibers: _*)
      dumpStrs   <- ZIO.foreach(dumps)(_.prettyPrintM)
    } yield {
      treeString + "\n" + dumpStrs.mkString
    }

}

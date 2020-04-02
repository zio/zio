package zio.internal

import scala.annotation.tailrec

import zio.Fiber.Dump
import zio.Fiber.Status.{ Done, Finishing, Running, Suspended }
import zio.{ Fiber, UIO, ZIO }

private[zio] object FiberRenderer {
  private def zipWithHasNext[A](it: Iterable[A]): Iterable[(A, Boolean)] =
    if (it.isEmpty)
      Seq.empty
    else {
      Iterable.concat(it.dropRight(1).map((_, true)), Seq((it.last, false)))
    }

  def prettyPrintM(dump: Fiber.Dump): UIO[String] = UIO(prettyPrint(dump, System.currentTimeMillis()))

  def prettyPrint(dump: Fiber.Dump, now: Long): String = {
    val millis  = (now - dump.fiberId.startTimeMillis)
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
       |${dump.trace.fold("")(_.prettyPrint)}
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

  def renderHierarchy(trees: Iterable[Dump]): String =
    zipWithHasNext(trees).map {
      case (tree, hasNext) => renderOne(tree, hasNext)
    }.mkString

  def renderOne(tree: Dump, hasNextSibling: Boolean): String = {
    def go(t: Dump, prefix: String, hasNext: Boolean): String = {
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

  @tailrec
  private def collectTraces(dumps: Iterable[Dump], acc: Vector[String], now: Long): Vector[String] = {
    val rootTraces = dumps.map(prettyPrint(_, now))
    val children   = dumps.flatMap(_.children)
    if (children.isEmpty) {
      acc
    } else {
      collectTraces(children, Vector.concat(rootTraces, acc), now)
    }
  }

  def dumpStr(fibers: Seq[Fiber.Runtime[_, _]], withTrace: Boolean): UIO[String] =
    for {
      dumps <- ZIO.foreach(fibers)(f => f.dumpWith(withTrace))
      now   <- UIO(System.currentTimeMillis())
    } yield {
      val treeString  = renderHierarchy(dumps)
      val dumpStrings = if (withTrace) collectTraces(dumps, Vector.empty, now) else Seq.empty
      (treeString +: dumpStrings).mkString("\n")
    }

}

package zio.internal

import zio.Fiber.Status.{ Done, Finishing, Running, Suspended }
import zio.{ Fiber, UIO, ZIO }

private[zio] object FiberRenderer {
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

  def renderHierarchy(fiber: Fiber.Runtime[_, _]): UIO[String] = {

    def go(f: Fiber.Runtime[_, _], prefix: String): UIO[String] =
      for {
        id              <- f.id
        name            <- f.getRef(Fiber.fiberName)
        status          <- f.status
        children        <- f.children
        newPrefix       = prefix + "|   "
        childrenResults <- ZIO.foreach(children)(c => go(c.asInstanceOf[Fiber.Runtime[_, _]], newPrefix))
      } yield {
        val nameStr   = name.fold("")(n => "\"" + n + "\" ")
        val statusMsg = renderStatus(status)
        s"${prefix}+---${nameStr}#${id.seqNumber} Status: $statusMsg\n" + childrenResults.mkString
      }

    go(fiber, "")
  }

  def dumpStr(fibers: Seq[Fiber.Runtime[_, _]]): UIO[String] =
    for {
      trees    <- ZIO.foreach(fibers)(FiberRenderer.renderHierarchy)
      dumps    <- Fiber.dump(fibers: _*)
      dumpStrs <- ZIO.foreach(dumps)(_.prettyPrintM)
    } yield {
      Seq.concat(trees, Seq("\n"), dumpStrs).mkString
    }

}

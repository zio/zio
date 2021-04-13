/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio.internal

import zio.Fiber.Dump
import zio.Fiber.Status.{Done, Finishing, Running, Suspended}
import zio.{Fiber, UIO, ZIO}

private[zio] object FiberRenderer {

  def dumpStr(fibers: Seq[Fiber.Runtime[_, _]], withTrace: Boolean): UIO[String] =
    for {
      dumps <- ZIO.foreach(fibers)(f => f.dumpWith(withTrace))
      now   <- UIO(System.currentTimeMillis())
    } yield {
      val treeString  = renderHierarchy(dumps)
      val dumpStrings = if (withTrace) collectTraces(dumps, now) else Seq.empty
      (treeString +: dumpStrings).mkString("\n")
    }

  def prettyPrintM(dump: Fiber.Dump): UIO[String] =
    UIO(prettyPrint(dump, System.currentTimeMillis()))

  private def zipWithHasNext[A](it: Iterable[A]): Iterable[(A, Boolean)] =
    if (it.isEmpty)
      Seq.empty
    else {
      Iterable.concat(it.dropRight(1).map((_, true)), Seq((it.last, false)))
    }

  private def prettyPrint(dump: Fiber.Dump, now: Long): String = {
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

  private def renderStatus(status: Fiber.Status): String =
    status match {
      case Done         => "Done"
      case Finishing(b) => "Finishing(" + (if (b) "interrupting" else "") + ")"
      case Running(b)   => "Running(" + (if (b) "interrupting" else "") + ")"
      case Suspended(_, interruptible, epoch, _, asyncTrace) =>
        val in = if (interruptible) "interruptible" else "uninterruptible"
        val ep = s"$epoch asyncs"
        val as = asyncTrace.map(_.prettyPrint).getOrElse("")
        s"Suspended($in, $ep, $as)"
    }

  private def renderHierarchy(trees: Iterable[Dump]): String =
    zipWithHasNext(trees).map { case (tree, _) =>
      renderOne(tree)
    }.mkString

  private def renderOne(tree: Dump): String = {
    def go(t: Dump, prefix: String): String = {
      val nameStr   = t.fiberName.fold("")(n => "\"" + n + "\" ")
      val statusMsg = renderStatus(t.status)
      s"${prefix}+---${nameStr}#${t.fiberId.seqNumber} Status: $statusMsg\n"
    }

    go(tree, "")
  }

  private def collectTraces(dumps: Iterable[Dump], now: Long): Vector[String] =
    dumps.map(prettyPrint(_, now)).toVector
}

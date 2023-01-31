package zio.test

import zio.{Ref, ZIO}

private[test] object TestDebug {
  // TODO parameterize output file based on the currently executing task
  val outputFile = "target/test-reports-zio/debug.txt"
  def outputFileForTask(task: String) = s"target/test-reports-zio/${task}_debug.txt"

  def createEmergencyFile(fullyQualifiedTaskName: String) = {
    import java.io.File

    makeOutputDirectory()
    val file = new File(outputFileForTask(fullyQualifiedTaskName))
    if(file.createNewFile()) {
      // we're good
    } else {
      file.delete()
      file.createNewFile()
    }
  }

  private def makeOutputDirectory() = {
    import java.nio.file.{Files, Paths}

    val fp = Paths.get(outputFile)
    Files.createDirectories(fp.getParent)
  }

  def deleteIfEmpty(fullyQualifiedTaskName: String) = {
    import java.io._
    import scala.io.Source

    val file = new File(outputFileForTask(fullyQualifiedTaskName))
    if (file.exists()) {
      val lines = Source.fromFile(file).getLines.filterNot(_.isBlank).toList
      if (lines.isEmpty) {
        file.delete()
      }
    }

  }


  def printEmergency(executionEvent: ExecutionEvent, lock: Ref.Synchronized[Unit]) =
    executionEvent match {
      case t @ ExecutionEvent.TestStarted(
            labelsReversed,
            annotations,
            ancestors,
            id,
            fullyQualifiedName
          ) =>
        write(s"${t.labels.mkString(" - ")} STARTED\n", true, lock)

      case t @ ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id, fullyQualifiedName) =>
        removeLine(t.labels.mkString(" - ") + " STARTED")
//        ZIO.unit

      case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) => ZIO.unit
      case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors)   => ZIO.unit
      case ExecutionEvent.TopLevelFlush(id) =>
        ZIO.unit
      case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) =>
        ZIO.unit
    }

  // TODO Dedup this with the same method in JVM/Native ResultFileOpsJson?
  def write(content: => String, append: Boolean, lock: Ref.Synchronized[Unit]): ZIO[Any, Nothing, Unit] =
    lock.updateZIO(_ =>
      ZIO
        .acquireReleaseWith(
          ZIO.attemptBlockingIO(new java.io.FileWriter(outputFile, append))
        )(f => ZIO.attemptBlocking(f.close()).orDie) { f =>
          ZIO.attemptBlockingIO(f.append(content))
        }
        .ignore
    )

  private def removeLine(searchString: String) = ZIO.succeed {
    import java.io._
    import scala.io.Source

    val lines = Source.fromFile(outputFile).getLines.filterNot(_.contains(searchString)).toList
    val pw    = new PrintWriter(outputFile)
    pw.write(lines.mkString("\n"))
    pw.close()
  }

}

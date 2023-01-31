package zio.test

import zio.{Ref, ZIO}

private[test] object TestDebug {
  private val outputDirectory                      = "target/test-reports-zio"
  private def outputFileForTask(task: String) = s"$outputDirectory/${task}_debug.txt"

  def createDebugFile(fullyQualifiedTaskName: String) = ZIO.succeed {
    import java.io.File

    makeOutputDirectory()
    val file = new File(outputFileForTask(fullyQualifiedTaskName))
    if (file.createNewFile()) {
      // we're good
    } else {
      file.delete()
      file.createNewFile()
    }
  }

  private def makeOutputDirectory() = {
    import java.nio.file.{Files, Paths}

    val fp = Paths.get(outputDirectory)
    Files.createDirectories(fp.getParent)
  }

  def deleteIfEmpty(fullyQualifiedTaskName: String) = ZIO.succeed{
    import java.io._
    import scala.io.Source

    val file = new File(outputFileForTask(fullyQualifiedTaskName))
    if (file.exists()) {
      val source = Source.fromFile(file)
      val nonBlankLines = source.getLines.filterNot(_.isBlank).toList
      source.close()
      if (nonBlankLines.isEmpty) {
        file.delete()
      }
    }

  }

  def printDebug(executionEvent: ExecutionEvent, lock: TestDebugFileLock) =
    executionEvent match {
      case t: ExecutionEvent.TestStarted =>
        write(t.fullyQualifiedName, s"${t.labels.mkString(" - ")} STARTED\n", true, lock)

      case t: ExecutionEvent.Test[_] =>
        removeLine(t.fullyQualifiedName, t.labels.mkString(" - ") + " STARTED", lock)

      case _ => ZIO.unit
    }

  private def write(
    fullyQualifiedTaskName: String,
    content: => String,
    append: Boolean,
    lock: TestDebugFileLock
  ): ZIO[Any, Nothing, Unit] =
    lock.updateFile(
      ZIO
        .acquireReleaseWith(
          ZIO.attemptBlockingIO(new java.io.FileWriter(outputFileForTask(fullyQualifiedTaskName), append))
        )(f => ZIO.attemptBlocking(f.close()).orDie) { f =>
          ZIO.attemptBlockingIO(f.append(content))
        }
        .ignore
    )

  private def removeLine(fullyQualifiedTaskName: String, searchString: String, lock: TestDebugFileLock) =
    lock.updateFile {
      ZIO.succeed {
        import java.io._
        import scala.io.Source

        val source = Source.fromFile(outputFileForTask(fullyQualifiedTaskName))

        val remainingLines =
          source.getLines.filterNot(_.contains(searchString))

        source.close()
        val pw = new PrintWriter(outputFileForTask(fullyQualifiedTaskName))
        pw.write(remainingLines.mkString("\n")+"\n")
        pw.close()
      }
    }

}

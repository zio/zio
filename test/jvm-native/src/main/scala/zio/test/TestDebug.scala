package zio.test

import zio.ZIO
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import scala.io.Source

private[test] object TestDebug {
  private val outputDirectory                 = "target/test-reports-zio"
  private def outputFileForTask(task: String) = s"$outputDirectory/${task}_debug.txt"

  def createDebugFile(fullyQualifiedTaskName: String): ZIO[Any, Nothing, Unit] = ZIO.succeed {
    makeOutputDirectory()
    val file = new File(outputFileForTask(fullyQualifiedTaskName))
    if (file.exists()) file.delete()
    file.createNewFile()
  }

  private def makeOutputDirectory() = {
    val fp = Paths.get(outputDirectory)
    Files.createDirectories(fp.getParent)
  }

  def deleteIfEmpty(fullyQualifiedTaskName: String): ZIO[Any, Nothing, Unit] = ZIO.succeed {
    val file = new File(outputFileForTask(fullyQualifiedTaskName))
    if (file.exists()) {
      val source        = Source.fromFile(file)
      val nonBlankLines = source.getLines.filterNot(isBlank).toList
      source.close()
      if (nonBlankLines.isEmpty) {
        file.delete()
      }
    }
  }

  private def isBlank(input: String): Boolean =
    input.toCharArray.forall(Character.isWhitespace(_))

  def print(executionEvent: ExecutionEvent, lock: TestDebugFileLock) =
    executionEvent match {
      case t: ExecutionEvent.TestStarted =>
        write(t.fullyQualifiedName, s"${t.labels.mkString(" - ")} STARTED\n", true, lock)

      case t: ExecutionEvent.Test[_] =>
        removeLine(t.fullyQualifiedName, t.labels.mkString(" - ") + " STARTED", lock)

      case _ => ZIO.unit
    }

  private def write(
    fullyQualifiedTaskName: String,
    content: String,
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
        val file = new File(outputFileForTask(fullyQualifiedTaskName))
        if (file.exists()) {
          val source = Source.fromFile(file)

          val remainingLines =
            source.getLines.filterNot(_.contains(searchString)).toList

          val pw = new PrintWriter(outputFileForTask(fullyQualifiedTaskName))
          pw.write(remainingLines.mkString("\n") + "\n")
          pw.close()
          source.close()
        }
      }
    }
}

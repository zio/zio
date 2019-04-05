package scalaz.zio.testkit

import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._

import com.github.ghik.silencer.silent

import sbt.testing._

final class ZioTestRunner(val args: Array[String], val remoteArgs: Array[String]) extends Runner {
  private val results: ConcurrentHashMap[String, Result] = new ConcurrentHashMap

  def done(): String =
    results.asScala.map {
      case (name, result) =>
        result match {
          case Succeeded => s"${Console.BOLD}${Console.GREEN}\u2713 ${name}${Console.RESET}"
          case Failed(e) =>
            println(s"${"=" * 10}${name}${"=" * 10}")
            e.printStackTrace
            println("-" * 30)
            s"${Console.BOLD}${Console.RED}\u2717 ${name}${Console.RESET}\n${Console.YELLOW}${e.getMessage}${Console.RESET}"
        }
    }.mkString("\n")

  def tasks(defs: Array[TaskDef]): Array[Task] = defs.map(taskDefToTask)

  private def taskDefToTask(td: TaskDef): Task = new Task {
    private def loadObject[T](name: String): T =
      Class.forName(s"${name}$$").getField("MODULE$").get(null).asInstanceOf[T]
    override def taskDef(): TaskDef = td
    override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
      loggers.foreach(_.debug(s"Using args: ${ZioTestRunner.arrayToString(args)}"))
      loggers.foreach(_.debug(s"Using remoteArgs: ${ZioTestRunner.arrayToString(remoteArgs)}"))

      val testsuite = loadObject[TestSpec](td.fullyQualifiedName)

      testsuite.tests.map {
        case (name, io) =>
          val result = testsuite
            .unsafeRunSync(io)
            .getOrElse(e => Failed(e.defects.headOption.getOrElse(new Exception("Unknown error"))))
          val _ = results.put(name, result)

          eventHandler.handle(new Event {
            def throwable(): OptionalThrowable = result match {
              case Failed(e) => new OptionalThrowable(e)
              case _         => new OptionalThrowable()
            }

            def fullyQualifiedName = td.fullyQualifiedName
            def status(): Status   = if (throwable().isDefined) Status.Failure else Status.Success
            def selector()         = new TestSelector(fullyQualifiedName())
            def duration()         = 0
            def fingerprint = new SubclassFingerprint {
              val superclassName          = "scalaz.zio.test.TestSpec"
              val isModule                = true
              val requireNoArgConstructor = false
            }
          })
      }

      Array.empty
    }
    override def tags(): Array[String] = Array.empty
  }
}

object ZioTestRunner {
  @silent def apply(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): ZioTestRunner =
    new ZioTestRunner(args, remoteArgs)

  private def arrayToString(a: Array[String]): String = Arrays.toString(a.asInstanceOf[Array[AnyRef]])
}

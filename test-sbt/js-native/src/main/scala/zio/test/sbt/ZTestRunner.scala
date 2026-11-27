package zio.test.sbt

import sbt.testing.{Task, TaskDef}
import zio.test.Summary

/**
 * The sbt runner of ZIO Test, created by [[ZTestFramework]].
 *
 * On the JVM, the specs of each `tasks` call run on one runtime built from
 * their `bootstrap` layers. The runner collects the summary of each spec, and
 * `done()` prints the combined summary and returns `"Completed tests"`.
 *
 * On Scala.js and Scala Native, a runner is a `ZTestRunner.Master` or a
 * `ZTestRunner.Slave`. There is no shared runtime: each spec runs as its own
 * application with its own `bootstrap` layer. `done()` returns the combined
 * summary instead of printing it.
 */
sealed abstract class ZTestRunner(
  args: Array[String],
  remoteArgs: Array[String],
  testClassLoader: ClassLoader,
  runnerType: String
) extends TestRunner(
      args,
      remoteArgs,
      testClassLoader,
      runnerType
    ) {

  /**
   * Adds the spec summary that a slave runner sent to the summaries of this
   * runner, and returns `None`. A message that is not a summary is ignored.
   */
  final override def receiveMessage(summary: String): Option[String] = {
    ZTestRunner.deserialize(summary).foreach(addSummary)
    None
  }
  // On Scala.js, Runtime.unsafe.fromLayer() fails:
  //   java.lang.Error: Cannot block for result to be set in JavaScript
  //   at zio.internal.OneShot.get
  //   at zio.Runtime$$anon$1.run
  //   at zio.Runtime$unsafe$.fromLayer
  // On Scala Native, using shared layer does not work for reasons unknown.
  // If this can be made to work on Scala.js and/or Scala Native, support for shared runtime can be added.
  final override protected def sharedRuntimeSupported: Boolean = false

  final override protected def returnSummary: Boolean = true

  /** Serializes `task` as its task definition. */
  final override def serializeTask(task: Task, serializer: TaskDef => String): String =
    serializer(task.taskDef())

  // This is what prevents us from utilizing merged Specs.
  // When we try to round trip, we only deserialize the first task, so all the others
  // that were merged in are lost.
  /**
   * Returns a task for the task definition that `deserializer` reads from
   * `task`, loading its spec object.
   */
  final override def deserializeTask(task: String, deserializer: String => TaskDef): Task =
    toTask(deserializer(task))
}

/**
 * The two kinds of ZIO Test sbt runner on Scala.js and Scala Native.
 */
object ZTestRunner {

  /**
   * The runner that sbt creates with `ZTestFramework.runner`. It collects the
   * summaries of the specs it runs and the summaries that slave runners send,
   * and `done()` returns the combined summary.
   */
  final class Master(
    args: Array[String],
    remoteArgs: Array[String],
    testClassLoader: ClassLoader
  ) extends ZTestRunner(args, remoteArgs, testClassLoader, runnerType = "master") {
    override protected def sendSummary(summary: Summary): Unit = addSummary(summary)
  }

  /**
   * The runner that sbt creates with `ZTestFramework.slaveRunner`. It sends the
   * summary of each spec it runs through `send` and keeps no summaries itself.
   */
  final class Slave(
    args: Array[String],
    remoteArgs: Array[String],
    testClassLoader: ClassLoader,
    send: String => Unit
  ) extends ZTestRunner(args, remoteArgs, testClassLoader, runnerType = "slave") {
    override protected def sendSummary(summary: Summary): Unit = send(serialize(summary))
  }

  // On Scala.js and Scala Native, ZTestRunner uses strings to send and receive
  // summaries. To transmit structured Summary data, we need to serialize to and
  // from strings.
  private def serialize(summary: Summary): String =
    List(
      summary.success.toString,
      summary.fail.toString,
      summary.ignore.toString,
      summary.failureDetails
    ).map(escape).mkString("\t")

  private def deserialize(s: String): Option[Summary] =
    s.split('\t').map(unescape) match {
      case Array(success, fail, ignore, summary) =>
        Some(Summary(success.toInt, fail.toInt, ignore.toInt, summary))

      case _ => None
    }

  private def escape(token: String): String   = token.replace("\t", "\\t")
  private def unescape(token: String): String = token.replace("\\t", "\t")
}

package zio.test.sbt

import sbt.testing.{Task, TaskDef}
import zio.test.Summary

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

  final override def serializeTask(task: Task, serializer: TaskDef => String): String =
    serializer(task.taskDef())

  // This is what prevents us from utilizing merged Specs.
  // When we try to round trip, we only deserialize the first task, so all the others
  // that were merged in are lost.
  final override def deserializeTask(task: String, deserializer: String => TaskDef): Task =
    toTask(deserializer(task))
}

object ZTestRunner {
  final class Master(
    args: Array[String],
    remoteArgs: Array[String],
    testClassLoader: ClassLoader
  ) extends ZTestRunner(args, remoteArgs, testClassLoader, runnerType = "master") {
    override protected def sendSummary(summary: Summary): Unit = addSummary(summary)
  }

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

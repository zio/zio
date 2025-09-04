package zio.test.sbt

import sbt.testing.{Event, EventHandler, Selector, SuiteSelector, TaskDef}
import zio.ZIO
import zio.test.{ZIOSpecAbstract, testConsole}

import scala.collection.mutable.ArrayBuffer

object ExecuteSpecs {
  def getOutput(
    spec: ZIOSpecAbstract,
    args: Array[String] = Array.empty
  ): ZIO[Any, Throwable, Seq[String]] =
    getOutputs(Seq(spec), args).mapError(_.head)

  def getOutputs(
    specs: Seq[ZIOSpecAbstract],
    args: Array[String] = Array.empty
  ): ZIO[Any, ::[Throwable], Seq[String]] =
    getOutputsAndEvents(specs, args).map(_._1)

  def getEvents(
    spec: ZIOSpecAbstract,
    args: Array[String] = Array.empty,
    selectors: Array[Selector] = Array(new SuiteSelector)
  ): ZIO[Any, ::[Throwable], Seq[Event]] =
    getOutputsAndEvents(Seq(spec), args, selectors).map(_._2)

  def getOutputsAndEvents(
    specs: Seq[ZIOSpecAbstract],
    args: Array[String],
    selectors: Array[Selector] = Array(new SuiteSelector)
  ): ZIO[Any, ::[Throwable], (Seq[String], Seq[Event])] = ZIO.suspendSucceed {
    val events = ArrayBuffer.empty[Event]

    def attemptBlocking[T](f: => T): ZIO[Any, ::[Throwable], T] = ZIO
      .attemptBlocking(f)
      .mapError((error: Throwable) => ::(error, Nil))

    for {
      testConsole <- testConsole
      taskDefs: Array[TaskDef] =
        specs
          .map(_.getClass.getName)
          .map(TestRunner.moduleName)
          .map(new TaskDef(_, ZioSpecFingerprint, false, selectors))
          .toArray
      runner <- attemptBlocking(new ZTestFramework().runner(args))
      tasks  <- attemptBlocking(runner.tasks(taskDefs, testConsole))
      _      <- ZIO.validate(tasks.toList)(_.run((e: Event) => events.append(e)))
      _      <- attemptBlocking(runner.done())
      output <- testConsole.output
    } yield (output, events.toSeq)
  }
}

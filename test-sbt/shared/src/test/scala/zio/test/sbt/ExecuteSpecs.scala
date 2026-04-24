package zio.test.sbt

import sbt.testing.{Event, Selector, SuiteSelector, TaskDef}
import zio.ZIO
import zio.test.{ZIOSpecAbstract, testConsole}

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
  ): ZIO[Any, ::[Throwable], (Seq[String], Seq[Event])] = {
    def attemptBlocking[T](f: => T): ZIO[Any, ::[Throwable], T] =
      ZIO
        .attemptBlocking(f)
        .mapError((error: Throwable) => ::(error, Nil))

    for {
      console <- testConsole
      taskDefs: Seq[TaskDef] =
        specs.map { spec =>
          val className  = spec.getClass.getName
          val moduleName = TestRunner.moduleName(className)
          new TaskDef(moduleName, ZioSpecFingerprint, false, selectors)
        }
      v <- attemptBlocking {
             val runner = new ZTestFramework().runner(args)
             (runner, runner.tasks(taskDefs, console))
           }
      (runner, tasks) = v
      events          = Array.newBuilder[Event]
      _              <- ZIO.validate(tasks)(_.run((e: Event) => events += e))
      _              <- attemptBlocking(runner.done())
      output         <- console.output
    } yield (output, events.result())
  }
}

package zio.test.sbt

import org.portablescala.reflect.Reflect
import sbt.testing.{Event, Selector, SuiteSelector, TaskDef}
import zio.ZIO
import zio.test.{ZIOSpecAbstract, testConsole}

import java.lang.reflect.Modifier
import scala.util.control.Exception.catching

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
    val taskDefs = discoverTasks(specs.map(_.getClass.getName), selectors)
    runTaskDefs(taskDefs, args)
  }

  def getOutputForDiscoveredName(
    name: String,
    args: Array[String] = Array.empty,
    selectors: Array[Selector] = Array(new SuiteSelector)
  ): ZIO[Any, ::[Throwable], Seq[String]] = {
    val taskDefs: Seq[TaskDef] = discoverTasks(Seq(name), selectors)
    runTaskDefs(
      taskDefs,
      args
    ).map(_._1)
  }

  private def mkTaskDef(
    name: String,
    selectors: Array[Selector]
  ): TaskDef = {
    val moduleName = TestRunner.moduleName(name)
    if (isModule(moduleName))
      new TaskDef(moduleName, ZioSpecFingerprint, false, selectors)
    else
      new TaskDef(name, ZioSpecClassFingerprint, false, selectors)
  }

  def getOutputForDiscoveredNamesMatching(
    names: Seq[String],
    pattern: String,
    args: Array[String] = Array.empty,
    selectors: Array[Selector] = Array(new SuiteSelector)
  ): ZIO[Any, ::[Throwable], Seq[String]] = {
    val taskDefs = discoverTasks(names, selectors)
      .filter(hasPattern(pattern))
    runTaskDefs(taskDefs, args).map(_._1)
  }

  private def isModule(moduleName: String): Boolean =
    Reflect
      .lookupLoadableModuleClass(moduleName, getClass.getClassLoader)
      .isDefined

  private def hasPattern(pattern: String)(td: TaskDef): Boolean =
    td.fullyQualifiedName().stripSuffix("$").contains(pattern)

  private def discoverTasks(names: Seq[String], selectors: Array[Selector]): Seq[TaskDef] = {
    val loader = getClass.getClassLoader
    names
      .filter(name => isConcreteDiscoverableName(name, loader))
      .map(name => mkTaskDef(name, selectors))
  }

  // Keep object specs discoverable via their module name (e.g. FooSpec -> FooSpec$).
  // For non-module names, only keep concrete classes:
  // - reject traits / interfaces
  // - reject abstract classes
  // - reject missing classes
  private def isConcreteDiscoverableName(name: String, loader: ClassLoader): Boolean = {
    val moduleName = TestRunner.moduleName(name)
    isModule(moduleName) || loadClassOption(name, loader).exists(isConcrete)
  }

  private def loadClassOption(name: String, loader: ClassLoader): Option[Class[?]] =
    catching(classOf[ClassNotFoundException]).opt(loader.loadClass(name))

  private def isConcrete(clazz: Class[_]): Boolean =
    !clazz.isInterface && !Modifier.isAbstract(clazz.getModifiers)

  private def runTaskDefs(
    taskDefs: Seq[TaskDef],
    args: Array[String]
  ): ZIO[Any, ::[Throwable], (Seq[String], Seq[Event])] = {
    def attemptBlocking[T](f: => T): ZIO[Any, ::[Throwable], T] =
      ZIO
        .attemptBlocking(f)
        .mapError((error: Throwable) => ::(error, Nil))

    for {
      console <- testConsole
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

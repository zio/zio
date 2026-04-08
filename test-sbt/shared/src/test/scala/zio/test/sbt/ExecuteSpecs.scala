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
    val taskDefs: Seq[TaskDef] =
      specs.map { spec =>
        val className  = spec.getClass.getName
        val moduleName = TestRunner.moduleName(className)
        new TaskDef(moduleName, ZioSpecFingerprint, false, selectors)
      }
    runTaskDefs(taskDefs, args)
  }

  def getOutputForDiscoveredName(
    name: String,
    args: Array[String] = Array.empty,
    selectors: Array[Selector] = Array(new SuiteSelector)
  ): ZIO[Any, ::[Throwable], Seq[String]] = {
    val taskDefs = Seq(taskDefForDiscoveredName(name, selectors))
    runTaskDefs(
      taskDefs,
      args
    ).map(_._1)
  }

  private def taskDefForDiscoveredName(
    name: String,
    selectors: Array[Selector]
  ): TaskDef = {
    val moduleName = TestRunner.moduleName(name)
    val isModule =
      Reflect
        .lookupLoadableModuleClass(moduleName, getClass.getClassLoader)
        .isDefined

    if (isModule)
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
    val loader = getClass.getClassLoader

    val taskDefs =
      names
        .filter(name => isConcreteDiscoverableName(name, loader))
        .map(taskDefForDiscoveredName(_, selectors))
        .filter { taskDef =>
          val fqcn = taskDef.fullyQualifiedName()
          fqcn.contains(pattern) || fqcn.stripSuffix("$").contains(pattern)
        }

    runTaskDefs(taskDefs, args).map(_._1)
  }

  // Keep object specs discoverable via their module name (e.g. FooSpec -> FooSpec$).
  // For non-module names, only keep concrete classes:
  // - reject traits / interfaces
  // - reject abstract classes
  // - reject missing classes
  private def loadClassOption(name: String, loader: ClassLoader): Option[Class[?]] =
    catching(classOf[ClassNotFoundException]).opt(loader.loadClass(name))

  private def isConcreteDiscoverableName(name: String, loader: ClassLoader): Boolean = {
    val moduleName = TestRunner.moduleName(name)

    if (Reflect.lookupLoadableModuleClass(moduleName, loader).isDefined)
      true
    else
      loadClassOption(name, loader).exists { clazz =>
        !clazz.isInterface && !Modifier.isAbstract(clazz.getModifiers)
      }
  }

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

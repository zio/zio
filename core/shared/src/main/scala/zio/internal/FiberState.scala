package zio.internal

import zio.Fiber.Status
import zio.{Callback, Cause, Exit, Fiber, IO, InterruptStatus, Supervisor, TracingStatus, ZScope}

import scala.annotation.tailrec

private[zio] final class FiberState[E, A](executing0: FiberState.Executing[E, A]) extends Serializable {
  import FiberState._

  private[this] var executing: Executing[E, A] = executing0
  private[this] var done: Exit[E, A]           = null.asInstanceOf[Exit[E, A]]

  def isExecuting: Boolean = executing ne null
  def isDone: Boolean      = done ne null

  def addObserver(callback: Callback[Nothing, Exit[E, A]]): Unit =
    if (isExecuting) executing.observers = callback :: executing.observers

  def getDone: Exit[E, A] = done

  def getInterrupted: Cause[Nothing] = executing.interrupted

  def getStatus: Fiber.Status = executing.status

  def isInterrupting: Boolean = {
    @tailrec
    def loop(status0: Fiber.Status): Boolean =
      status0 match {
        case Status.Running(b)                      => b
        case Status.Finishing(b)                    => b
        case Status.Suspended(previous, _, _, _, _) => loop(previous)
        case _                                      => false
      }

    isExecuting && loop(executing.status)
  }

  def observers: List[Callback[Nothing, Exit[E, A]]] =
    if (isExecuting) executing.observers else Nil

  def removeObserver(callback: Callback[Nothing, Exit[E, A]]): Unit =
    if (isExecuting) executing.observers = executing.observers.filter(_ ne callback)

  def setDone(exit: Exit[E, A]): Unit = {
    executing = null
    done = exit
  }
}
object FiberState extends Serializable {
  def apply[E, A](
    startIStatus: InterruptStatus,
    startEnv: AnyRef,
    startExec: Executor,
    supervisor0: Supervisor[Any],
    initialTracingStatus: TracingStatus
  ): FiberState[E, A] =
    new FiberState(new Executing(startIStatus, startEnv, startExec, supervisor0, initialTracingStatus))

  class Executing[E, A](
    startIStatus: InterruptStatus,
    startEnv: AnyRef,
    startExec: Executor,
    supervisor0: Supervisor[Any],
    initialTracingStatus: TracingStatus
  ) {
    val stack: Stack[Any => IO[Any, Any]]                        = Stack()
    var status: Fiber.Status                                     = Status.Running(false)
    var observers: List[Callback[Nothing, Exit[E, A]]]           = Nil
    var interrupted: Cause[Nothing]                              = Cause.empty
    @volatile var asyncEpoch: Long                               = 0L
    val interruptStatus: StackBool                               = StackBool(startIStatus.toBoolean)
    var currentEnvironment: Any                                  = startEnv
    var currentExecutor: Executor                                = startExec
    var currentSupervisor: Supervisor[Any]                       = supervisor0
    var currentForkScopeOverride: Option[ZScope[Exit[Any, Any]]] = None
    var currentTracingStatus: TracingStatus                      = initialTracingStatus
    var scopeKey: ZScope.Key                                     = null
  }
}

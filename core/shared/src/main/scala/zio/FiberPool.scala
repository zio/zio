package zio

import FiberPool.{ Shutdown, State }

import scala.collection.immutable.{ Queue => IQueue }

/**
 * A fiber-based equivalent of an ExecutorService. A fiber pool lazily spawns fibers that
 * handle submitted tasks, and keeps them alive for additional tasks submitted.
 *
 * Tasks can be submitted using the [[FiberPool#submit]] method; the pool may be shutdown
 * using the [[FiberPool#shutdown]] method.
 *
 * Fiber pools are usually more efficient for long-running processes in which tasks
 * must be executed in a bounded amount of parallelism compared to repeatedly spawning
 * new fibers.
 */
class FiberPool(state: Ref[State], workerLimit: Long, defectHandler: Cause[Nothing] => UIO[Any]) {

  /**
   * Submits a new task to the pool. This method will semantically block until a fiber is available
   * to start the task. Defects caused by the task will be handled by the pool's defect handler.
   *
   * If the pool is already shutdown, this method will interrupt its caller.
   */
  def submit[R](task: URIO[R, Any]): URIO[R, Any] =
    ZIO.environment[R].flatMap { r =>
      Promise.make[Nothing, Unit].flatMap { taskReceived =>
        val wrappedTask = task.catchAllCause(defectHandler(_).sandbox.ignore).provide(r)

        state.modify {
          case s @ State(workerCount, freeWorkers, pendingTasks, shutdownType, workers) =>
            shutdownType match {
              case None =>
                if (pendingTasks.nonEmpty) {
                  // To ensure fairness, we always queue up behind tasks that are
                  // already pending.
                  (
                    taskReceived.await,
                    State(
                      workerCount,
                      freeWorkers,
                      pendingTasks.enqueue(taskReceived.succeed(()).as(wrappedTask)),
                      shutdownType,
                      workers
                    )
                  )

                } else if (freeWorkers.nonEmpty) {
                  // There were no pending tasks, but we did find an existing
                  // free worker. So hand off the task to it.
                  val (worker, rest) = freeWorkers.dequeue

                  (worker(wrappedTask), State(workerCount, rest, pendingTasks, shutdownType, workers))
                } else if (workerCount < workerLimit) {
                  // No pending tasks, no free workers, but the number of workers
                  // is below the limit. So launch a new worker.
                  (addWorker(wrappedTask), State(workerCount + 1, freeWorkers, pendingTasks, shutdownType, workers))
                } else {
                  // No free resources, so queue up the task in the pending tasks
                  // queue.
                  (
                    taskReceived.await,
                    State(
                      workerCount,
                      freeWorkers,
                      pendingTasks.enqueue(taskReceived.succeed(()).as(wrappedTask)),
                      shutdownType,
                      workers
                    )
                  )
                }
              case _ => (ZIO.interrupt, s)
            }
        }.flatten
      }

    }

  /**
   * Shuts down the fiber pool. Behavior is dependent on the specified [[Shutdown]].
   */
  def shutdown(shutdownType: Shutdown): UIO[Any] =
    state.modify { s =>
      shutdownType match {
        case Shutdown.Immediate =>
          (Fiber.interruptAll(s.workers), s.copy(shutdown = Some(shutdownType)))
        case Shutdown.DrainRunning | Shutdown.DrainPending =>
          (
            ZIO.foreach(s.freeWorkers)(_.apply(ZIO.interrupt)) *> Fiber.awaitAll(s.workers),
            s.copy(shutdown = Some(shutdownType))
          )
      }
    }.flatten

  private def addWorker(task: UIO[Any]): UIO[Any] =
    (task *> workerLoop).forkDaemon.tap { worker =>
      state.modify { s =>
        s.shutdown match {
          case Some(Shutdown.Immediate) => (worker.interrupt, s)
          case _                              => (UIO.unit, s.copy(workers = worker :: s.workers))
        }
      }.flatten
    }

  private val workerLoop: UIO[Nothing] =
    Promise
      .make[Nothing, UIO[Any]]
      .flatMap { nextTask =>
        state.modify {
          case s @ State(workerCount, freeWorkers, pendingTasks, shutdownType, workers) =>
            shutdownType match {
              case None =>
                if (pendingTasks.nonEmpty) {
                  val (task, rest) = pendingTasks.dequeue
                  (task.flatten, State(workerCount, freeWorkers, rest, shutdownType, workers))
                } else
                  (
                    nextTask.await.flatten,
                    State(workerCount, freeWorkers.enqueue(nextTask.succeed(_)), pendingTasks, shutdownType, workers)
                  )

              case Some(Shutdown.DrainPending) =>
                if (pendingTasks.nonEmpty) {
                  val (task, rest) = pendingTasks.dequeue
                  (
                    task.flatten,
                    State(workerCount, freeWorkers, rest, shutdownType, workers)
                  )
                } else
                  (
                    ZIO.interrupt,
                    State(workerCount, freeWorkers, pendingTasks, shutdownType, workers)
                  )

              case _ =>
                (ZIO.interrupt, s)
            }

        }.flatten
      }
      .forever
}

object FiberPool {

  /**
   * Creates a new FiberPool bounded at `limit` fibers with the specified defect handler.
   */
  def make(limit: Long, defectHandler: Cause[Nothing] => UIO[Any] = _ => UIO.unit): UIO[FiberPool] =
    Ref.make(State(0, IQueue.empty, IQueue.empty, None, Nil)).map(new FiberPool(_, limit, defectHandler))

  private case class State(
    workerCount: Long,
    freeWorkers: IQueue[UIO[Any] => UIO[Any]],
    pendingTasks: IQueue[UIO[UIO[Any]]],
    shutdown: Option[Shutdown],
    workers: List[Fiber[Nothing, Nothing]]
  )

  /**
   * Specifies the behavior when shutting down a [[FiberPool]]. See the individual
   * subtypes for details on behavior.
   */
  sealed trait Shutdown
  object Shutdown {

    /**
     * Immediately shuts down the pool by interrupting all of its fibers, regardless
     * if they are executing a task or not.
     */
    case object Immediate extends Shutdown

    /**
     * Shuts the pool down and waits for currently running tasks to finish.
     */
    case object DrainRunning extends Shutdown

    /**
     * Shuts the pool down and waits for currently running and pending tasks to finish.
     */
    case object DrainPending extends Shutdown
  }
}

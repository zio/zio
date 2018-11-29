// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent._
import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong, AtomicReference }
import scala.annotation.{ switch, tailrec }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scalaz.zio.ExitResult.Cause
import scalaz.zio.internal.OneShot

/**
 * This trait provides a high-performance implementation of a runtime system for
 * the `IO` monad on the JVM.
 */
trait RTS {
  import RTS._

  /**
   * Effectfully and synchronously interprets an `IO[E, A]`, either throwing an
   * error, running forever, or producing an `A`.
   */
  final def unsafeRun[E, A](io: IO[E, A]): A = unsafeRunSync(io).toEither.fold(throw _, identity)

  final def unsafeRunAsync[E, A](io: IO[E, A])(k: Callback[E, A]): Unit = {
    val context = newFiberContext[E, A](defaultHandler)
    context.evaluate(io)
    context.runAsync(k)
  }

  /**
   * Effectfully interprets an `IO`, blocking if necessary to obtain the result.
   */
  final def unsafeRunSync[E, A](io: IO[E, A]): ExitResult[E, A] = {
    val context = newFiberContext[E, A](defaultHandler)
    context.evaluate(io)
    context.await
  }

  final def unsafeShutdownAndWait(timeout: Duration): Unit = {
    if (timeout == Duration.Zero) {
      scheduledExecutor.shutdownNow()
      threadPool.shutdownNow()
    } else {
      scheduledExecutor.shutdown()
      scheduledExecutor.awaitTermination(timeout.toMillis, TimeUnit.MILLISECONDS)
      threadPool.shutdown()
      threadPool.awaitTermination(timeout.toMillis, TimeUnit.MILLISECONDS)
    }
    ()
  }

  /**
   * The default handler for unhandled exceptions in the main fiber, and any
   * fibers it forks that recursively inherit the handler.
   */
  def defaultHandler: Cause[Any] => IO[Nothing, Unit] =
    (cause: Cause[Any]) => console.putStrLn(FiberFailure(cause).getMessage)

  /**
   * The main thread pool used for executing fibers.
   */
  val threadPool: ExecutorService = newDefaultThreadPool()

  /**
   * The thread pool for scheduling timed tasks.
   */
  lazy val scheduledExecutor: ScheduledExecutorService = newDefaultScheduledExecutor()

  /**
   * Determines the maximum number of operations executed by a fiber before
   * yielding to other fibers.
   *
   * FIXME: Replace this entirely with the new scheme.
   */
  val YieldMaxOpCount = 1024

  private final def newFiberContext[E, A](handler: Cause[Any] => IO[Nothing, Unit]): FiberContext[E, A] = {
    val nextFiberId = fiberCounter.incrementAndGet()
    val context     = new FiberContext[E, A](this, nextFiberId, handler)

    context
  }

  final def submit[A](block: => A): Unit = {
    threadPool.submit(new Runnable {
      def run: Unit = { block; () }
    })

    ()
  }

  final def schedule[E, A](block: => A, duration: Duration): Async[E, Unit] =
    if (duration == Duration.Zero) {
      submit(block)

      Async.later
    } else {
      val future = scheduledExecutor.schedule(new Runnable {
        def run: Unit = submit(block)
      }, duration.toNanos, TimeUnit.NANOSECONDS)

      Async.maybeLater(IO.sync { future.cancel(true); () })
    }

  /** Utility function to avoid catching truly fatal exceptions. Do not allocate
   * memory here since this would defeat the point of checking for OOME.
   */
  protected def nonFatal(t: Throwable): Boolean =
    !t.isInstanceOf[VirtualMachineError]
}

private object RTS {

  /**
   * The global counter for assigning fiber identities on creation.
   */
  private val fiberCounter = new AtomicLong(0)

  final class Stack() {
    type Cont = Any => IO[_, Any]

    private[this] var array   = new Array[AnyRef](13)
    private[this] var size    = 0
    private[this] var nesting = 0

    final def isEmpty: Boolean = size == 0

    final def push(a: Cont): Unit =
      if (size == 13) {
        array = Array(array, a, null, null, null, null, null, null, null, null, null, null, null)
        size = 2
        nesting += 1
      } else {
        array(size) = a
        size += 1
      }

    final def pop(): Cont = {
      val idx = size - 1
      var a   = array(idx)
      if (idx == 0 && nesting > 0) {
        array = a.asInstanceOf[Array[AnyRef]]
        a = array(12)
        array(12) = null // GC
        size = 12
        nesting -= 1
      } else {
        array(idx) = null // GC
        size = idx
      }
      a.asInstanceOf[Cont]
    }
  }

  /**
   * An implementation of Fiber that maintains context necessary for evaluation.
   */
  final class FiberContext[E, A](rts: RTS, val fiberId: FiberId, val unhandled: Cause[Any] => IO[Nothing, Unit])
      extends Fiber[E, A] {
    import java.util.{ Collections, Set, WeakHashMap }
    import FiberState._
    import rts.YieldMaxOpCount

    // Accessed from multiple threads:
    private[this] val state  = new AtomicReference[FiberState[E, A]](FiberState.Initial[E, A])
    private[this] var killed = false

    // TODO: A lot can be pulled out of state to increase performance
    // Also the size of this structure should be minimized with laziness used
    // to optimize further, to make forking a cheaper operation.

    // Accessed from within a single thread (not necessarily the same):
    @volatile private[this] var noInterrupt                               = 0
    @volatile private[this] var supervised: List[Set[FiberContext[_, _]]] = Nil
    @volatile private[this] var supervising                               = 0

    private[this] val stack: Stack = new Stack()

    final def runAsync(k: Callback[E, A]): Unit =
      register(k) match {
        case Async.Now(v) => k(v)
        case _            =>
      }

    /**
     * Awaits for the result of the fiber to be computed.
     */
    final def await: ExitResult[E, A] = {
      val result = OneShot.make[ExitResult[E, A]]

      runAsync(result.set(_))

      result.get
    }

    private class Finalizer(val finalizer: IO[Nothing, Unit]) extends Function[Any, IO[E, Any]] {
      final def apply(v: Any): IO[E, Any] = {
        noInterrupt += 1

        finalizer.flatMap(_ => IO.sync { noInterrupt -= 1; v })
      }
    }

    /**
     * Unwinds the stack, collecting all finalizers and coalescing them into an
     * `IO` that produces an option of a cause of finalizer failures. If needed,
     * catch exceptions and apply redeem error handling.
     */
    final def unwindStack(catchError: Boolean): IO[Nothing, Option[Cause[Nothing]]] = {
      def zipCauses(c1: Option[Cause[Nothing]], c2: Option[Cause[Nothing]]): Option[Cause[Nothing]] =
        c1.flatMap(c1 => c2.map(c1 ++ _)).orElse(c1).orElse(c2)

      var errorHandler: Any => IO[Any, Any]              = null
      var finalizer: IO[Nothing, Option[Cause[Nothing]]] = null

      // Unwind the stack, looking for exception handlers and coalescing
      // finalizers.
      while ((errorHandler eq null) && !stack.isEmpty) {
        stack.pop() match {
          case a: IO.Redeem[_, _, _, _] if catchError =>
            errorHandler = a.err.asInstanceOf[Any => IO[Any, Any]]
          case f0: Finalizer =>
            val f: IO[Nothing, Option[Cause[Nothing]]] =
              f0.finalizer.sandboxed.redeemPure[Nothing, Option[Cause[Nothing]]](Some(_), _ => None)
            if (finalizer eq null) finalizer = f
            else finalizer = finalizer.seqWith(f)(zipCauses)
          case _ =>
        }
      }

      // We need to maintain the invariant that an empty stack means the
      // exception was *not* caught.
      // The stack will never be empty if the error was caught, because
      // the error handler will be pushed onto the stack.
      // This lets us return only the finalizer, which will be null for common cases,
      // and result in zero heap allocations for the happy path.
      if (errorHandler ne null) stack.push(errorHandler)

      finalizer
    }

    /**
     * The main interpreter loop for `IO` actions. For purely synchronous actions,
     * this will run to completion unless required to yield to other fibers.
     * For mixed actions, the loop will proceed no further than the first
     * asynchronous boundary.
     *
     * @param io0 The `IO` to evaluate on the fiber.
     */
    final def evaluate(io0: IO[E, _]): Unit = {
      // Do NOT accidentally capture any of local variables in a closure,
      // or Scala will wrap them in ObjectRef and performance will plummet.
      var curIo: IO[E, Any] = io0.as[Any]

      while (curIo ne null) {
        try {
          // Put the maximum operation count on the stack for fast access:
          val maxopcount = YieldMaxOpCount

          var opcount: Int = 0

          while (curIo ne null) {
            // Check to see if the fiber should continue executing or not:
            if (!shouldDie) {
              // Fiber does not need to be interrupted, but might need to yield:
              if (opcount == maxopcount) {
                // Cooperatively yield to other fibers currently suspended.
                // FIXME: Replace with the new design.
                opcount = 0

                // Cannot capture `curIo` since it will be boxed into `ObjectRef`,
                // which destroys performance, so we create a temp val here.
                val tmpIo = curIo

                rts.submit(evaluate(tmpIo))

                curIo = null
              } else {
                // Fiber is neither being interrupted nor needs to yield. Execute
                // the next instruction in the program:
                (curIo.tag: @switch) match {
                  case IO.Tags.FlatMap =>
                    val io = curIo.asInstanceOf[IO.FlatMap[E, Any, Any]]

                    val nested = io.io

                    // A mini interpreter for the left side of FlatMap that evaluates
                    // anything that is 1-hop away. This eliminates heap usage for the
                    // happy path.
                    (nested.tag: @switch) match {
                      case IO.Tags.Point =>
                        val io2 = nested.asInstanceOf[IO.Point[E]]

                        curIo = io.flatMapper(io2.value())

                      case IO.Tags.Strict =>
                        val io2 = nested.asInstanceOf[IO.Strict[Any]]

                        curIo = io.flatMapper(io2.value)

                      case IO.Tags.SyncEffect =>
                        val io2 = nested.asInstanceOf[IO.SyncEffect[Any]]

                        curIo = io.flatMapper(io2.effect())

                      case IO.Tags.Descriptor =>
                        val value = getDescriptor

                        curIo = io.flatMapper(value)

                      case _ =>
                        // Fallback case. We couldn't evaluate the LHS so we have to
                        // use the stack:
                        curIo = nested

                        stack.push(io.flatMapper)
                    }

                  case IO.Tags.Point =>
                    val io = curIo.asInstanceOf[IO.Point[Any]]

                    val value = io.value()

                    curIo = nextInstr(value)

                  case IO.Tags.Strict =>
                    val io = curIo.asInstanceOf[IO.Strict[Any]]

                    val value = io.value

                    curIo = nextInstr(value)

                  case IO.Tags.SyncEffect =>
                    val io = curIo.asInstanceOf[IO.SyncEffect[Any]]

                    val value = io.effect()

                    curIo = nextInstr(value)

                  case IO.Tags.AsyncEffect =>
                    val io = curIo.asInstanceOf[IO.AsyncEffect[E, Any]]

                    // By default, we'll resume asynchronously, so this
                    // evaluation loop should halt:
                    curIo = null

                    // Enter suspended state:
                    val cancel = enterAsync()

                    if (cancel != null) {
                      // It's possible we were interrupted prior to entering
                      // suspended state. So we have to check that condition,
                      // and if so, do not initiate the async effect (because
                      // otherwise, it would not be interrupted).
                      try {
                        if (state.get.interrupted && noInterrupt == 0) curIo = IO.interrupt
                        else
                          io.register(resumeAsync) match {
                            case Async.Now(value) =>
                              if (shouldResumeAsync()) curIo = IO.done(value)

                            case Async.MaybeLater(cancel0) => cancel.set(cancel0)
                          }
                      } finally {
                        // May not allow exit of the code block without the
                        // cancel action being set (could hang interruptor!):
                        if (!cancel.isSet) cancel.set(IO.unit)
                      }
                    }

                  case IO.Tags.Redeem =>
                    val io = curIo.asInstanceOf[IO.Redeem[E, Any, Any, Any]]

                    curIo = io.value

                    stack.push(io)

                  case IO.Tags.Fork =>
                    val io = curIo.asInstanceOf[IO.Fork[_, Any]]

                    val optHandler = io.handler

                    val handler = if (optHandler eq None) unhandled else optHandler.get

                    val value: FiberContext[_, Any] = fork(io.value, handler)

                    supervise(value)

                    curIo = nextInstr(value)

                  case IO.Tags.Uninterruptible =>
                    val io = curIo.asInstanceOf[IO.Uninterruptible[E, Any]]

                    curIo = doNotInterrupt(io.io)

                  case IO.Tags.Sleep =>
                    val io = curIo.asInstanceOf[IO.Sleep]

                    curIo = IO.async0[E, Any] { k =>
                      rts
                        .schedule(k(SuccessUnit), io.duration)
                    }

                  case IO.Tags.Supervise =>
                    val io = curIo.asInstanceOf[IO.Supervise[E, Any]]

                    curIo = enterSupervision *>
                      io.value.ensuring(exitSupervision(io.supervisor))

                  case IO.Tags.Fail =>
                    val io = curIo.asInstanceOf[IO.Fail[E]]

                    val finalizer = unwindStack(!io.cause.interrupted)

                    if (stack.isEmpty) {
                      // Error not caught, stack is empty:
                      if (finalizer eq null) {
                        // No finalizer, so immediately produce the error.
                        curIo = null

                        val cause = if (state.get.interrupted) io.cause ++ Cause.interrupted else io.cause

                        done(ExitResult.failed(cause))
                      } else {
                        // We have finalizers to run. We'll resume executing with the
                        // uncaught failure after we have executed all the finalizers:
                        curIo = doNotInterrupt(finalizer).flatMap(
                          cause => IO.fail0(cause.foldLeft(io.cause)(_ ++ _))
                        )
                      }
                    } else {
                      // Error caught:
                      if (finalizer eq null) {
                        curIo = nextInstr(io.cause)
                      } else {
                        curIo = doNotInterrupt(finalizer).map(cause => cause.foldLeft(io.cause)(_ ++ _))
                      }
                    }

                  case IO.Tags.Ensuring =>
                    val io = curIo.asInstanceOf[IO.Ensuring[E, Any]]
                    stack.push(new Finalizer(io.finalizer))
                    curIo = io.io

                  case IO.Tags.Descriptor =>
                    val value = getDescriptor

                    curIo = nextInstr(value)
                }
              }
            } else {
              // Interruption cannot be interrupted:
              this.noInterrupt += 1

              // Fiber was interrupted
              curIo = IO.interrupt
            }

            opcount = opcount + 1
          }
        } catch {
          // Catastrophic error handler. Any error thrown inside the interpreter is
          // either a bug in the interpreter or a bug in the user's code. Let the
          // fiber die but attempt finalization & report errors.
          case t: Throwable if (rts.nonFatal(t)) =>
            // Interruption cannot be interrupted:
            this.noInterrupt += 1

            curIo = IO.terminate(t)
        }
      }
    }

    private[this] final def getDescriptor: Fiber.Descriptor =
      Fiber.Descriptor(fiberId, state.get.interrupted, ExecutionContext.fromExecutor(rts.threadPool), unhandled)

    /**
     * Forks an `IO` with the specified failure handler.
     */
    final def fork[E, A](io: IO[E, A], handler: Cause[Any] => IO[Nothing, Unit]): FiberContext[E, A] = {
      val context = rts.newFiberContext[E, A](handler)

      rts.submit(context.evaluate(io))

      context
    }

    /**
     * Resumes a synchronous evaluation given the newly produced value.
     *
     * @param value The value which will be used to resume the sync evaluation.
     */
    private[this] final def resumeEvaluate(value: ExitResult[E, Any]): Unit =
      value match {
        case ExitResult.Succeeded(v) =>
          val io = nextInstr(v)

          if (!(io eq null)) evaluate(io)

        case ExitResult.Failed(cause) => evaluate(IO.fail0(cause))
      }

    /**
     * Resumes an asynchronous computation.
     *
     * @param value The value produced by the asynchronous computation.
     */
    private[this] final val resumeAsync: ExitResult[E, Any] => Unit =
      value => if (shouldResumeAsync()) resumeEvaluate(value)

    final def interrupt: IO[Nothing, Unit] = IO.async0[Nothing, Unit](kill0(_))

    final def observe: IO[Nothing, ExitResult[E, A]] = IO.async0(observe0)

    final def poll: IO[Nothing, Option[ExitResult[E, A]]] = IO.sync(poll0)

    private[this] final def enterSupervision: IO[E, Unit] = IO.sync {
      supervising += 1

      def newWeakSet[A]: Set[A] = Collections.newSetFromMap[A](new WeakHashMap[A, java.lang.Boolean]())

      val set = newWeakSet[FiberContext[_, _]]

      supervised = set :: supervised
    }

    private[this] final def supervise(child: FiberContext[_, _]): Unit =
      if (supervising > 0) {
        supervised match {
          case Nil =>
          case set :: _ =>
            set.add(child)

            ()
        }
      }

    @tailrec
    private[this] final def enterAsync(): OneShot[Canceler] = {
      val oldState = state.get
      val cancel   = OneShot.make[Canceler]

      oldState match {
        case Executing(interrupted, _, observers) =>
          val newState = Executing(interrupted, FiberStatus.Suspended(cancel), observers)

          if (!state.compareAndSet(oldState, newState)) enterAsync()
          else cancel

        case _ => null
      }
    }

    @tailrec
    private[this] final def shouldResumeAsync(): Boolean = {
      val oldState = state.get

      oldState match {
        case Executing(interrupted, FiberStatus.Suspended(_), observers) =>
          if (!state.compareAndSet(oldState, Executing(interrupted, FiberStatus.Running, observers)))
            shouldResumeAsync()
          else true

        case _ => false
      }
    }

    private[this] final def exitSupervision(
      supervisor: Iterable[Fiber[_, _]] => IO[Nothing, Unit]
    ): IO[Nothing, Unit] = {
      import collection.JavaConverters._
      IO.flatten(IO.sync {
        supervising -= 1

        var action = IO.unit

        supervised = supervised match {
          case Nil => Nil
          case set :: tail =>
            action = supervisor(set.asScala)
            tail
        }

        action
      })
    }

    @inline
    private[this] final def shouldDie: Boolean = killed && noInterrupt == 0

    @inline
    private[this] final def nextInstr(value: Any): IO[E, Any] =
      if (!stack.isEmpty) stack.pop()(value).asInstanceOf[IO[E, Any]]
      else {
        done(ExitResult.succeeded(value.asInstanceOf[A]))

        null
      }

    private[this] final val exitUninterruptible: IO[Nothing, Unit] = IO.sync { noInterrupt -= 1 }

    private[this] final def doNotInterrupt[E, A](io: IO[E, A]): IO[E, A] = {
      this.noInterrupt += 1
      io.ensuring(exitUninterruptible)
    }

    private[this] final def register(cb: Callback[E, A]): Async[E, A] =
      observe0 {
        case ExitResult.Succeeded(r)  => cb(r)
        case ExitResult.Failed(cause) => cb(ExitResult.failed(cause))
      }.fold(ExitResult.failed(_), identity)

    @tailrec
    private[this] final def done(v: ExitResult[E, A]): Unit = {
      val oldState = state.get

      oldState match {
        case Executing(_, _, observers) =>
          if (!state.compareAndSet(oldState, Done(v))) done(v)
          else {
            notifyObservers(v, observers)
            reportUnhandled(v)
          }

        case Done(_) => // Huh?
      }
    }

    private[this] final def reportUnhandled(v: ExitResult[E, A]): Unit = v match {
      case ExitResult.Failed(cause) => rts.submit(rts.unsafeRun(unhandled(cause)))
      case _                        =>
    }

    private[this] final def makeInterruptObserver(k: Callback[Nothing, Unit]): Callback[Nothing, ExitResult[E, A]] =
      _ => k(SuccessUnit)

    @tailrec
    private[this] final def kill0(k: Callback[Nothing, Unit]): Async[Nothing, Unit] = {

      val oldState = state.get

      oldState match {
        case Executing(_, FiberStatus.Suspended(cancel), observers0) if noInterrupt == 0 =>
          val observers = makeInterruptObserver(k) :: observers0

          if (!state.compareAndSet(oldState, Executing(true, FiberStatus.Running, observers))) kill0(k)
          else {
            killed = true

            // Interruption may not be interrupted:
            noInterrupt += 1

            rts.submit(evaluate(cancel.get *> IO.interrupt))

            Async.later
          }

        case Executing(_, status, observers0) =>
          val observers = makeInterruptObserver(k) :: observers0

          if (!state.compareAndSet(oldState, Executing(true, status, observers))) kill0(k)
          else {
            killed = true
            Async.later
          }

        case Done(_) => Async.now(SuccessUnit)
      }
    }

    @tailrec
    private[this] final def observe0(k: Callback[Nothing, ExitResult[E, A]]): Async[Nothing, ExitResult[E, A]] = {
      val oldState = state.get

      oldState match {
        case Executing(interrupted, status, observers0) =>
          val observers = k :: observers0

          if (!state.compareAndSet(oldState, Executing(interrupted, status, observers))) observe0(k)
          else Async.later

        case Done(v) => Async.now(ExitResult.succeeded(v))
      }
    }

    private[this] final def poll0: Option[ExitResult[E, A]] =
      state.get match {
        case Done(r) => Some(r)
        case _       => None
      }

    private[this] final def notifyObservers(
      v: ExitResult[E, A],
      observers: List[Callback[Nothing, ExitResult[E, A]]]
    ): Unit = {
      val result = ExitResult.succeeded(v)

      // To preserve fair scheduling, we submit all resumptions on the thread
      // pool in order of their submission.
      observers.reverse.foreach(k => rts.submit(k(result)))
    }
  }
  sealed abstract class FiberStatus extends Serializable with Product
  object FiberStatus {
    final case object Running                                      extends FiberStatus
    final case class Suspended(cancel: OneShot[IO[Nothing, Unit]]) extends FiberStatus
  }

  sealed abstract class FiberState[+E, +A] extends Serializable with Product {

    /** indicates if the fiber was interrupted */
    def interrupted: Boolean

  }
  object FiberState extends Serializable {
    final case class Executing[E, A](
      interrupted: Boolean,
      status: FiberStatus,
      observers: List[Callback[Nothing, ExitResult[E, A]]]
    ) extends FiberState[E, A]
    final case class Done[E, A](value: ExitResult[E, A]) extends FiberState[E, A] {
      def interrupted: Boolean = value.interrupted
    }

    def Initial[E, A] = Executing[E, A](false, FiberStatus.Running, Nil)
  }

  val SuccessUnit: ExitResult[Nothing, Unit] = ExitResult.succeeded(())

  final def newDefaultThreadPool(): ExecutorService = {
    val corePoolSize  = Runtime.getRuntime.availableProcessors() * 2
    val keepAliveTime = 1000L
    val timeUnit      = TimeUnit.MILLISECONDS
    val workQueue     = new LinkedBlockingQueue[Runnable]()
    val threadFactory = new NamedThreadFactory("zio", true)

    val threadPool = new ThreadPoolExecutor(
      corePoolSize,
      corePoolSize,
      keepAliveTime,
      timeUnit,
      workQueue,
      threadFactory
    )
    threadPool.allowCoreThreadTimeOut(true)

    threadPool
  }

  final def newDefaultScheduledExecutor(): ScheduledExecutorService =
    Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

  final class NamedThreadFactory(name: String, daemon: Boolean) extends ThreadFactory {

    private val parentGroup =
      Option(System.getSecurityManager).fold(Thread.currentThread().getThreadGroup)(_.getThreadGroup)

    private val threadGroup = new ThreadGroup(parentGroup, name)
    private val threadCount = new AtomicInteger(1)
    private val threadHash  = Integer.toUnsignedString(this.hashCode())

    override def newThread(r: Runnable): Thread = {
      val newThreadNumber = threadCount.getAndIncrement()

      val thread = new Thread(threadGroup, r)
      thread.setName(s"$name-$newThreadNumber-$threadHash")
      thread.setDaemon(daemon)

      thread
    }

  }
}

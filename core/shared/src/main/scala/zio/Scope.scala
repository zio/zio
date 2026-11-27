/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.LongMap

/**
 * A `Scope` collects finalizers and runs them when it is closed. It is the
 * basis of resource management in ZIO: a scoped effect, of type `ZIO[Scope, E,
 * A]`, such as one built with `ZIO.acquireRelease`, adds the release of the
 * resource it acquires to the `Scope` in its environment, and `ZIO.scoped`
 * makes a new scope, provides it to the effect and closes it when the effect
 * completes by success, failure or interruption.
 *
 * `addFinalizer` and `addFinalizerExit` add a finalizer, `extend` provides this
 * scope to an effect without closing it, and `fork` makes a child scope that is
 * closed when this scope is closed. Closing is not part of `Scope` itself:
 * `Scope.make` returns a `Scope.Closeable`, whose `close` runs the finalizers
 * with the `Exit` value it is given. A scope made with `Scope.make` runs its
 * finalizers one after another, in the reverse of the order in which they were
 * added; a scope made with `Scope.parallel` runs them in parallel, and
 * `Scope.makeWith` takes any `ExecutionStrategy`.
 *
 * {{{
 * def openFile(log: Ref[List[String]], name: String): ZIO[Scope, Nothing, String] =
 *   ZIO.acquireRelease(log.update(_ :+ s"open \$name").as(name))(n => log.update(_ :+ s"close \$n"))
 *
 * val steps: UIO[List[String]] =
 *   for {
 *     log <- Ref.make(List.empty[String])
 *     _ <- ZIO.scoped {
 *            for {
 *              input  <- openFile(log, "input.csv")
 *              output <- openFile(log, "output.csv")
 *              _      <- log.update(_ :+ s"copy \$input to \$output")
 *            } yield ()
 *          }
 *     result <- log.get
 *   } yield result
 * // List(open input.csv, open output.csv, copy input.csv to output.csv, close output.csv, close input.csv)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/resource/scope/ Scope]]
 */
sealed trait Scope extends Serializable { self =>

  /**
   * Adds a finalizer to this scope. The finalizer runs when the scope is
   * closed, and receives the `Exit` value the scope is closed with. When the
   * scope is already closed, the finalizer runs at once with the `Exit` value
   * the scope was closed with. `Scope.global` discards the finalizer.
   */
  def addFinalizerExit(finalizer: Exit[Any, Any] => UIO[Any])(implicit trace: Trace): UIO[Unit]

  /**
   * Forks a new scope that is a child of this scope. Finalizers added to the
   * child scope will be run according to the specified `ExecutionStrategy`. The
   * child scope will automatically be closed when this scope is closed.
   */
  def forkWith(executionStrategy: => ExecutionStrategy)(implicit trace: Trace): UIO[Scope.Closeable]

  /**
   * A simplified version of `addFinalizerExit` when the `finalizer` does not
   * depend on the `Exit` value that the scope is closed with.
   */
  final def addFinalizer(finalizer: => UIO[Any])(implicit trace: Trace): UIO[Unit] =
    addFinalizerExit(_ => finalizer)

  /**
   * The execution strategy finalizers associated with this scope will be run
   * with.
   */
  def executionStrategy: ExecutionStrategy =
    ExecutionStrategy.Sequential

  /**
   * Extends the scope of a `ZIO` workflow that needs a scope into this scope by
   * providing it to the workflow but not closing the scope when the workflow
   * completes execution. This allows extending a scoped value into a larger
   * scope.
   */
  final def extend[R]: Scope.ExtendPartiallyApplied[R] =
    new Scope.ExtendPartiallyApplied[R](self)

  /**
   * Forks a new scope that is a child of this scope. Finalizers added to the
   * child scope run with the execution strategy of this scope, so the child of
   * a scope made with `Scope.make` runs them sequentially, in the reverse of
   * the order in which they were added, and the child of a scope made with
   * `Scope.parallel` runs them in parallel. The child scope will automatically
   * be closed when this scope is closed, at the place the fork has among the
   * finalizers of this scope. Closing the child first removes it from this
   * scope.
   *
   * {{{
   * val closingOrder: UIO[List[String]] =
   *   for {
   *     log    <- Ref.make(List.empty[String])
   *     parent <- Scope.make
   *     _      <- parent.addFinalizer(log.update(_ :+ "parent: close pool"))
   *     child  <- parent.fork
   *     _      <- child.addFinalizer(log.update(_ :+ "child: close connection"))
   *     _      <- parent.close(Exit.unit)
   *     result <- log.get
   *   } yield result
   * // List(child: close connection, parent: close pool)
   * }}}
   */
  final def fork(implicit trace: Trace): UIO[Scope.Closeable] =
    forkWith(executionStrategy)
}

/**
 * Constructors of scopes (`make`, `makeWith`, `parallel`), the `global` scope,
 * the `default` layer, and accessors that add a finalizer to the `Scope` in the
 * environment.
 */
object Scope {

  /**
   * The `Tag` of `Scope`, which `ZEnvironment` uses to store and find the scope
   * it holds.
   */
  implicit val tag: Tag[Scope] = Tag(EnvironmentTag.tagFromTagMacro[Scope])

  /**
   * A `Scope` that can also be closed. `Scope.make`, `Scope.makeWith`,
   * `Scope.parallel` and `fork` return one, so the code that creates a scope
   * can close it, while code that receives a plain `Scope` cannot close it.
   */
  sealed trait Closeable extends Scope { self =>

    /**
     * Closes a scope with the specified exit value, running all finalizers that
     * have been added to the scope.
     *
     * Closing a scope that is already closed does nothing. When a finalizer
     * dies, the other finalizers still run, and the returned effect dies with
     * the defects of the finalizers that died.
     *
     * {{{
     * val flushed: UIO[(List[String], Boolean)] =
     *   for {
     *     log    <- Ref.make(List.empty[String])
     *     scope  <- Scope.make
     *     _      <- scope.addFinalizer(log.update(_ :+ "flush cache"))
     *     _      <- scope.addFinalizer(ZIO.die(new IllegalStateException("disk unavailable")))
     *     exit   <- scope.close(Exit.unit).exit
     *     result <- log.get
     *   } yield (result, exit.isFailure)
     * // (List(flush cache),true)
     * }}}
     */
    def close(exit: => Exit[Any, Any])(implicit trace: Trace): UIO[Unit]

    /**
     * Returns the number of finalizers that have been added to this scope and
     * not yet finalized.
     */
    def size: Int

    /**
     * Uses the scope by providing it to a `ZIO` workflow that needs a scope,
     * guaranteeing that the scope is closed with the result of that workflow as
     * soon as the workflow completes execution, whether by success, failure, or
     * interruption.
     */
    final def use[R]: Scope.UsePartiallyApplied[R] =
      new Scope.UsePartiallyApplied[R](self)
  }

  /**
   * Accesses a scope in the environment and adds a finalizer to it.
   */
  def addFinalizer(finalizer: => UIO[Any])(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    addFinalizerExit(_ => finalizer)

  /**
   * Accesses a scope in the environment and adds a finalizer to it.
   */
  def addFinalizerExit(finalizer: Exit[Any, Any] => UIO[Any])(implicit
    trace: Trace
  ): ZIO[Scope, Nothing, Unit] =
    ZIO.environmentWithZIO[Scope](_.getScope.addFinalizerExit(finalizer))

  /**
   * A layer that constructs a scope and closes it when the workflow the layer
   * is provided to completes execution, whether by success, failure, or
   * interruption. This can be used to close a scope when providing a layer to a
   * workflow.
   */
  val default: ZLayer[Any, Nothing, Scope] =
    ZLayer.scopedEnvironment(
      ZIO
        .acquireReleaseExit(Scope.make(Trace.empty))((scope, exit) => scope.close(exit)(Trace.empty))(
          Trace.empty
        )
        .map(ZEnvironment.empty.unsafe.addScope(_)(Unsafe))(Trace.empty)
    )(Trace.empty)

  /**
   * The global scope which is never closed. Finalizers added to this scope will
   * be immediately discarded and closing this scope has no effect.
   */
  val global: Scope.Closeable =
    new Scope.Closeable {
      final def addFinalizerExit(finalizer: Exit[Any, Any] => UIO[Any])(implicit trace: Trace): UIO[Unit] =
        ZIO.unit
      final def close(exit: => Exit[Any, Any])(implicit trace: Trace): UIO[Unit] =
        ZIO.unit
      final def forkWith(executionStrategy: => ExecutionStrategy)(implicit trace: Trace): UIO[Scope.Closeable] =
        makeWith(executionStrategy)
      final def size: Int = 0
    }

  /**
   * Makes a scope. Finalizers added to this scope will be run sequentially in
   * the reverse of the order in which they were added when this scope is
   * closed.
   */
  def make(implicit trace: Trace): UIO[Scope.Closeable] =
    ZIO.succeed(unsafe.make(Unsafe))

  /**
   * Makes a scope. Finalizers added to this scope will be run according to the
   * specified `ExecutionStrategy`.
   */
  def makeWith(executionStrategy0: => ExecutionStrategy)(implicit trace: Trace): UIO[Scope.Closeable] =
    ZIO.succeed(unsafe.makeWith(executionStrategy0)(Unsafe))

  /**
   * Makes a scope. Finalizers added to this scope will be run in parallel when
   * this scope is closed.
   */
  def parallel(implicit trace: Trace): UIO[Scope.Closeable] =
    makeWith(ExecutionStrategy.Parallel)

  /**
   * Constructors that create a scope at once, outside of a `ZIO` effect. They
   * need an `Unsafe` value.
   */
  object unsafe {

    /**
     * Creates a scope whose finalizers run sequentially, in the reverse of the
     * order in which they were added, when it is closed.
     */
    def make(implicit unsafe: Unsafe): Scope.Closeable =
      new CloseableScope(ExecutionStrategy.Sequential)

    /**
     * Creates a scope whose finalizers run with the specified
     * `ExecutionStrategy` when it is closed.
     */
    def makeWith(executionStrategy: ExecutionStrategy)(implicit unsafe: Unsafe): Scope.Closeable =
      new CloseableScope(executionStrategy)

    private final class CloseableScope(override val executionStrategy: ExecutionStrategy) extends Scope.Closeable {
      private[this] val releaseMap = new ReleaseMap

      def addFinalizerExit(finalizer: Exit[Any, Any] => UIO[Any])(implicit trace: Trace): UIO[Unit] =
        releaseMap.addDiscard(finalizer)

      def close(exit: => Exit[Any, Any])(implicit trace: Trace): UIO[Unit] =
        releaseMap.releaseAll(exit, executionStrategy)

      def forkWith(executionStrategy: => ExecutionStrategy)(implicit trace: Trace): UIO[Scope.Closeable] =
        ZIO.uninterruptible {
          val scope = Scope.unsafe.makeWith(executionStrategy)(Unsafe)
          releaseMap
            .add(scope.close(_))
            .flatMap(scope.addFinalizerExit)
            .zipRight(Exit.succeed(scope))
        }

      def size: Int =
        releaseMap.size
    }
  }

  /**
   * The result of `Scope#extend`, which lets the caller name the rest of the
   * environment, `R`, before passing the effect.
   */
  final class ExtendPartiallyApplied[R](private val scope: Scope) extends AnyVal {

    /**
     * Provides the scope to `zio`, leaving the environment `R` to be provided,
     * and leaves the scope open when `zio` completes.
     */
    def apply[E, A](zio: => ZIO[Scope with R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      zio.provideSomeEnvironment[R](_.unsafe.addScope(scope)(Unsafe))
  }

  /**
   * The result of `Scope.Closeable#use`, which lets the caller name the rest of
   * the environment, `R`, before passing the effect.
   */
  final class UsePartiallyApplied[R](private val scope: Scope.Closeable) extends AnyVal {

    /**
     * Provides the scope to `zio`, leaving the environment `R` to be provided,
     * and closes the scope with the `Exit` value of `zio` when `zio` completes
     * by success, failure or interruption.
     */
    def apply[E, A](zio: => ZIO[Scope with R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      scope.extend[R](zio).onExit(scope.close(_))
  }

  private type Finalizer = Exit[Any, Any] => UIO[Any]

  private sealed abstract class State
  private object State {
    // The sorting order of the LongMap uses bit ordering (000, 001, ... 111 but with 64 bits). This
    // works out to be `0 ... Long.MaxValue, Long.MinValue, ... -1`. The order of the map is mainly
    // important for the finalization, in which we want to walk it in reverse order. So we insert
    // into the map using keys that will build it in reverse. That way, when we do the final iteration,
    // the finalizers are already in correct order.
    final val firstKey       = -1L
    final val initial: State = Running(firstKey, LongMap.empty)

    final case class Exited(exit: Exit[Any, Any])                           extends State
    final case class Running(nextKey: Long, finalizers: LongMap[Finalizer]) extends State
  }

  /**
   * A `ReleaseMap` represents the finalizers associated with a scope.
   *
   * The design of `ReleaseMap` is inspired by ResourceT, written by Michael
   * Snoyman @snoyberg.
   * (https://github.com/snoyberg/conduit/blob/master/resourcet/Control/Monad/Trans/Resource/Internal.hs)
   */
  private final class ReleaseMap extends AtomicReference[State](State.initial) with Serializable {
    ref: AtomicReference[State] =>
    import State.{Exited, Running}

    private[this] def next(l: Long) =
      if (l == 0L) throw new RuntimeException("ReleaseMap wrapped around")
      else if (l == Long.MinValue) Long.MaxValue
      else l - 1

    private[this] def modify[B](f: State => (UIO[B], State))(implicit trace: Trace): UIO[B] =
      ZIO.suspendSucceed {
        var b = null.asInstanceOf[UIO[B]]
        while (b eq null) {
          val current = ref.get()
          val kv      = f(current)
          val value   = kv._1
          val state   = kv._2
          if (ref.compareAndSet(current, state)) b = value
        }
        b
      }

    /**
     * Adds a finalizer to the finalizers associated with this scope. If the
     * finalizers associated with this scope have already been run this
     * finalizer will be run immediately.
     *
     * The finalizer returned from this method will remove the original
     * finalizer from the map and run it.
     */
    def add(finalizer: Finalizer)(implicit trace: Trace): UIO[Finalizer] =
      modify {
        case Running(nextKey, fins) =>
          (
            Exit.succeed(release(nextKey, _)),
            Running(next(nextKey), fins.updated(nextKey, finalizer))
          )
        case exited @ Exited(exit) =>
          (
            ZIO.suspendSucceed(finalizer(exit)) *> ReleaseMap.noopFinalizer,
            exited
          )
      }

    /**
     * Adds a finalizer to the finalizers associated with this scope. If the
     * finalizers associated with this scope have already been run this
     * finalizer will be run immediately.
     */
    def addDiscard(finalizer: Finalizer)(implicit trace: Trace): UIO[Unit] =
      modify {
        case Running(nextKey, fins) =>
          (
            Exit.unit,
            Running(next(nextKey), fins.updated(nextKey, finalizer))
          )
        case exited @ Exited(exit) =>
          (
            ZIO.suspendSucceed(finalizer(exit).unit),
            exited
          )
      }

    /**
     * Runs the specified finalizer and removes it from the finalizers
     * associated with this scope.
     */
    def release(key: Long, exit: Exit[Any, Any])(implicit trace: Trace): UIO[Any] =
      modify {
        case s @ Running(_, fins) =>
          (
            ZIO.suspendSucceed {
              val fin = fins.getOrElse(key, null)
              if (fin eq null) Exit.unit
              else fin(exit)
            },
            s.copy(finalizers = fins - key)
          )
        case s: Exited => (Exit.unit, s)
      }

    /**
     * Runs the finalizers associated with this scope using the specified
     * execution strategy. After this action finishes, any finalizers added to
     * this scope will be run immediately.
     */
    def releaseAll(exit: Exit[Any, Any], execStrategy: ExecutionStrategy)(implicit trace: Trace): UIO[Unit] =
      modify {
        case s: Exited => (Exit.unit, s)
        case Running(nextKey, fins) =>
          val finalizer =
            if (nextKey == State.firstKey || fins.isEmpty)
              Exit.unit
            // NOTE: Don't call `.size` on `LongMap` as it's O(n)!
            // NOTE 2: This is not strictly accurate as we might have removed entries from the LongMap and have only 1 remaining.
            // But this should be extremely rare so we don't need to cater for it
            else if (nextKey == State.firstKey - 1L)
              ZIO.suspendSucceed(fins(fins.firstKey)(exit)).unit
            else {
              execStrategy match {
                case ExecutionStrategy.Sequential =>
                  ZIO.suspendSucceed {
                    var error = null.asInstanceOf[Cause[Nothing]]
                    val it    = fins.values.iterator
                    ZIO
                      .whileLoop(it.hasNext)(it.next()(exit).exit) {
                        case Exit.Failure(c) => error = if (error eq null) c else error ++ c
                        case _               => ()
                      }
                      .flatMap(_ => if (error eq null) Exit.unit else Exit.failCause(error))
                  }
                case ExecutionStrategy.Parallel =>
                  ZIO
                    .foreachPar(fins.values)(fin => fin(exit).exit)
                    .flatMap(Exit.collectAllParDiscard)

                case ExecutionStrategy.ParallelN(n) =>
                  ZIO
                    .foreachPar(fins.values)(fin => fin(exit).exit)
                    .flatMap(Exit.collectAllParDiscard)
                    .withParallelism(n)
              }
            }
          (finalizer, Exited(exit))
      }

    /**
     * Number of finalizers that have not yet been run
     */
    def size: Int =
      ref.get match {
        case _: Exited        => 0
        case Running(_, fins) => fins.size
      }
  }

  private object ReleaseMap {
    private val noopFinalizer = Exit.succeed((_: Exit[Any, Any]) => Exit.unit)

    /**
     * Creates a new ReleaseMap.
     */
    def make(implicit trace: Trace): UIO[ReleaseMap] =
      ZIO.succeed(new ReleaseMap)
  }
}

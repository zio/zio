/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.stm

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }

import scalaz.zio.{ IO, UIO }

import java.util.{ HashMap => MutableMap }
import scala.util.{ Failure, Success, Try }

/**
 * `STM[E, A]` represents an effect that can be performed transactionally,
 * resulting in a failure `E` or a value `A`.
 *
 * {{{
 * def transfer(receiver: TRef[Int],
 *              sender: TRef[Int], much: Int): UIO[Int] =
 *   STM.atomically {
 *     for {
 *       balance <- sender.get
 *       _       <- STM.check(balance >= much)
 *       _       <- receiver.update(_ + much)
 *       _       <- sender.update(_ - much)
 *       newAmnt <- receiver.get
 *     } yield newAmnt
 *   }
 *
 *   val action: UIO[Int] =
 *     for {
 *       t <- STM.atomically(TRef.make(0).zip(TRef.make(20000)))
 *       (receiver, sender) = t
 *       balance <- transfer(receiver, sender, 1000)
 *     } yield balance
 * }}}
 */
final class STM[+E, +A] private[stm] (
  val exec: STM.internal.Journal => STM.internal.TRez[E, A]
) extends AnyVal { self =>
  import STM.internal.{ resetJournal, TRez }

  /**
   * Sequentially zips this value with the specified one.
   */
  final def <*>[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    self zip that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * second element of the tuple.
   */
  final def <*[E1 >: E, B](that: => STM[E1, B]): STM[E1, A] =
    self zipLeft that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * first element of the tuple.
   */
  final def *>[E1 >: E, B](that: => STM[E1, B]): STM[E1, B] =
    self zipRight that

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  final def >>=[E1 >: E, B](f: A => STM[E1, B]): STM[E1, B] =
    self flatMap f

  /**
   * Simultaneously filters and maps the value produced by this effect.
   */
  final def collect[B](pf: PartialFunction[A, B]): STM[E, B] =
    new STM(
      journal =>
        (self exec journal) match {
          case t @ TRez.Fail(_) => t
          case TRez.Succeed(a)  => if (pf isDefinedAt a) TRez.Succeed(pf(a)) else TRez.Retry
          case TRez.Retry       => TRez.Retry
        }
    )

  /**
   * Commits this transaction atomically.
   */
  final def commit: IO[E, A] = STM.atomically(self)

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  final def const[B](b: => B): STM[E, B] = self map (_ => b)

  /**
   * Converts the failure channel into an `Either`.
   */
  final def either: STM[Nothing, Either[E, A]] =
    new STM(
      journal =>
        (self exec journal) match {
          case TRez.Fail(e)    => TRez.Succeed(Left(e))
          case TRez.Succeed(a) => TRez.Succeed(Right(a))
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Filters the value produced by this effect, retrying the transaction until
   * the predicate returns true for the value.
   */
  final def filter(f: A => Boolean): STM[E, A] =
    collect {
      case a if f(a) => a
    }

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  final def flatMap[E1 >: E, B](f: A => STM[E1, B]): STM[E1, B] =
    new STM(
      journal =>
        (self exec journal) match {
          case TRez.Succeed(a)  => f(a) exec journal
          case t @ TRez.Fail(_) => t
          case TRez.Retry       => TRez.Retry
        }
    )

  /**
   * Flattens out a nested `STM` effect.
   */
  final def flatten[E1 >: E, B](implicit ev: A <:< STM[E1, B]): STM[E1, B] =
    self flatMap ev

  /**
   * Folds over the `STM` effect, handling both failure and success, but not
   * retry.
   */
  final def fold[B](f: E => B, g: A => B): STM[Nothing, B] =
    new STM(
      journal =>
        (self exec journal) match {
          case TRez.Fail(e)    => TRez.Succeed(f(e))
          case TRez.Succeed(a) => TRez.Succeed(g(a))
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Effectfully folds over the `STM` effect, handling both failure and
   * success.
   */
  final def foldM[E1, B](f: E => STM[E1, B], g: A => STM[E1, B]): STM[E1, B] =
    new STM(
      journal =>
        (self exec journal) match {
          case TRez.Fail(e)    => f(e) exec journal
          case TRez.Succeed(a) => g(a) exec journal
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Maps the value produced by the effect.
   */
  final def map[B](f: A => B): STM[E, B] =
    new STM(
      journal =>
        (self exec journal) match {
          case TRez.Succeed(a)  => TRez.Succeed(f(a))
          case t @ TRez.Fail(_) => t
          case TRez.Retry       => TRez.Retry
        }
    )

  /**
   * Maps from one error type to another.
   */
  final def mapError[E1](f: E => E1): STM[E1, A] =
    new STM(
      journal =>
        (self exec journal) match {
          case t @ TRez.Succeed(_) => t
          case TRez.Fail(e)        => TRez.Fail(f(e))
          case TRez.Retry          => TRez.Retry
        }
    )

  /**
   * Converts the failure channel into an `Option`.
   */
  final def option: STM[Nothing, Option[A]] =
    fold[Option[A]](_ => None, Some(_))

  /**
   * Tries this effect first, and if it fails, tries the other effect.
   */
  final def orElse[E1, A1 >: A](that: => STM[E1, A1]): STM[E1, A1] =
    new STM(
      journal =>
        (self exec journal) match {
          case TRez.Fail(_)        => { resetJournal(journal); that exec journal }
          case t @ TRez.Succeed(_) => t
          case TRez.Retry          => { resetJournal(journal); that exec journal }
        }
    )

  /**
   * Returns a transactional effect that will produce the value of this effect in left side, unless it
   * fails, in which case, it will produce the value of the specified effect in right side.
   */
  final def orElseEither[E1 >: E, B](that: => STM[E1, B]): STM[E1, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * Maps the success value of this effect to unit.
   */
  final def unit: STM[E, Unit] = const(())

  /**
   * Maps the success value of this effect to unit.
   */
  @deprecated("use unit", "1.0.0")
  final def void: STM[E, Unit] = unit

  /**
   * Same as [[filter]]
   */
  final def withFilter(f: A => Boolean): STM[E, A] = filter(f)

  /**
   * Named alias for `<*>`.
   */
  final def zip[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    (self zipWith that)((a, b) => a -> b)

  /**
   * Named alias for `<*`.
   */
  final def zipLeft[E1 >: E, B](that: => STM[E1, B]): STM[E1, A] =
    (self zip that) map (_._1)

  /**
   * Named alias for `*>`.
   */
  final def zipRight[E1 >: E, B](that: => STM[E1, B]): STM[E1, B] =
    (self zip that) map (_._2)

  /**
   * Sequentially zips this value with the specified one, combining the values
   * using the specified combiner function.
   */
  final def zipWith[E1 >: E, B, C](that: => STM[E1, B])(f: (A, B) => C): STM[E1, C] =
    self flatMap (a => that map (b => f(a, b)))
}

object STM {

  private[stm] object internal {
    class Versioned[A](val value: A)

    type Journal =
      MutableMap[Long, STM.internal.Entry]

    type Todo = () => Unit

    /**
     * Resets the journal so that it does not modify any entries.
     */
    final def resetJournal(journal: Journal): Unit = {
      val it = journal.entrySet.iterator
      while (it.hasNext()) {
        it.next.getValue.reset()
      }
    }

    /**
     * Commits the journal.
     */
    final def commit(journal: Journal): Unit = {
      val it = journal.entrySet.iterator
      while (it.hasNext()) it.next.getValue.commit()
    }

    /**
     * Determines if the journal is valid.
     */
    final def isValid(journal: Journal): Boolean = {
      var valid = true
      val it    = journal.entrySet.iterator
      while (valid && it.hasNext()) {
        valid = it.next.getValue.isValid
      }
      valid
    }

    /**
     * Determines if the journal is invalid.
     */
    final def isInvalid(journal: Journal): Boolean = !isValid(journal)

    /**
     * Atomically collects and clears all the todos from any `TRef` that
     * participated in the transaction.
     */
    final def collectTodos(journal: Journal): MutableMap[Long, Todo] = {
      val allTodos  = new MutableMap[Long, Todo](4)
      val emptyTodo = Map.empty[Long, Todo]

      val it = journal.entrySet().iterator()

      while (it.hasNext()) {
        val tref = it.next().getValue().tref
        val todo = tref.todo

        var loop = true
        while (loop) {
          val oldTodo = todo.get

          loop = !todo.compareAndSet(oldTodo, emptyTodo)

          if (!loop) {
            import collection.JavaConverters._

            allTodos.putAll(oldTodo.asJava)
          }
        }
      }

      allTodos
    }

    /**
     * Executes the todos in the current thread, sequentially.
     */
    final def execTodos(todos: MutableMap[Long, Todo]): Unit = {
      val it = todos.entrySet().iterator()
      while (it.hasNext()) it.next().getValue().apply()
    }

    /**
     * For the given transaction id, adds the specified todo effect to all
     * `TRef` values.
     */
    final def addTodo(txnId: Long, journal: Journal, todoEffect: Todo): Unit = {
      val it = journal.entrySet().iterator()

      while (it.hasNext()) {
        val tref = it.next().getValue().tref

        var loop = true
        while (loop) {
          val oldTodo = tref.todo.get

          val newTodo = oldTodo updated (txnId, todoEffect)

          loop = !tref.todo.compareAndSet(oldTodo, newTodo)
        }
      }
    }

    final val succeedUnit: TRez[Nothing, Unit] =
      TRez.Succeed(())

    final def makeTRefId(): Long = trefCounter.incrementAndGet()

    final def makeTxnId(): Long = txnCounter.incrementAndGet()

    private[this] val trefCounter: AtomicLong = new AtomicLong()

    private[this] val txnCounter: AtomicLong = new AtomicLong()

    final val globalLock = new java.util.concurrent.Semaphore(1)

    sealed trait TRez[+A, +B] extends Serializable with Product
    object TRez {
      final case class Fail[A](value: A)    extends TRez[A, Nothing]
      final case class Succeed[B](value: B) extends TRez[Nothing, B]
      final case object Retry               extends TRez[Nothing, Nothing]
    }

    abstract class Entry {
      type A
      val tref: TRef[A]
      var newValue: A
      val expected: Versioned[A]

      final def unsafeSet(value: Any): Unit =
        newValue = value.asInstanceOf[A]

      final def unsafeGet[B]: B =
        newValue.asInstanceOf[B]

      /**
       * Commits the new value to the `TRef`.
       */
      final def commit(): Unit =
        tref.versioned = new Versioned(newValue)

      /**
       * Determines if the entry is invalid. This is the negated version of
       * `isValid`.
       */
      final def isInvalid: Boolean = !isValid

      /**
       * Determines if the entry is valid. That is, if the version of the
       * `TRef` is equal to the expected version.
       */
      final def isValid: Boolean =
        tref.versioned eq expected

      /**
       * Resets the value of this entry, so that if committed, it will have
       * no effect on the TRef.
       */
      final def reset(): Unit =
        newValue = expected.value
    }

    object Entry {

      /**
       * Creates an entry for the journal, given the `TRef` being updated, the
       * new value of the `TRef`, and the expected version of the `TRef`.
       */
      final def apply[A0](tref0: TRef[A0], newValue0: A0, expected0: Versioned[A0]): Entry =
        new Entry {
          type A = A0
          val tref     = tref0
          var newValue = newValue0
          val expected = expected0
        }
    }
  }

  import internal._

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  final def atomically[E, A](stm: STM[E, A]): IO[E, A] =
    UIO.effectTotalWith { platform =>
      val txnId = makeTxnId()

      val done = new AtomicBoolean(false)

      val ref = new AtomicReference(UIO[Unit](done synchronized {
        done set true
      }))

      IO.effectAsyncMaybe[E, A] { k =>
        import internal.globalLock

        def tryTxn(): Option[IO[E, A]] =
          if (done.get) None
          else
            done synchronized {
              if (done.get) None
              else {
                var journal = null.asInstanceOf[MutableMap[Long, Entry]]
                var value   = null.asInstanceOf[TRez[E, A]]

                var loop = true

                while (loop) {
                  journal =
                    if (journal eq null) new MutableMap[Long, Entry](4)
                    else {
                      journal.clear(); journal
                    }
                  value = stm exec journal

                  value match {
                    case _: TRez.Succeed[_] =>
                      globalLock.acquire()

                      try if (isValid(journal)) {
                        commit(journal)

                        loop = false
                      } finally globalLock.release()

                    case _: TRez.Fail[_] =>
                      globalLock.acquire()

                      try loop = isInvalid(journal)
                      finally globalLock.release()

                    case TRez.Retry =>
                      addTodo(txnId, journal, tryTxnAsync)

                      loop = false
                  }
                }

                def completed(io: IO[E, A]): Option[IO[E, A]] = {
                  done set true

                  val todos = collectTodos(journal)

                  if (todos.size > 0) platform.executor.submitOrThrow(() => execTodos(todos))

                  Some(io)
                }

                value match {
                  case TRez.Succeed(a) => completed(IO.succeed(a))
                  case TRez.Fail(e)    => completed(IO.fail(e))
                  case TRez.Retry =>
                    val stale = isInvalid(journal)

                    if (stale) tryTxn() else None
                }
              }
            }

        def tryTxnAsync: Todo = () => {
          tryTxn() match {
            case None     =>
            case Some(io) => k(io)
          }
        }

        tryTxn()
      } ensuring UIO(ref.get).flatten
    }.flatten

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  final def check(p: Boolean): STM[Nothing, Unit] =
    if (p) STM.unit else retry

  /**
   * Collects all the transactional effects in a list, returning a single
   * transactional effect that produces a list of values.
   */
  final def collectAll[E, A](i: Iterable[STM[E, A]]): STM[E, List[A]] =
    i.foldRight[STM[E, List[A]]](STM.succeed(Nil)) {
      case (stm, acc) =>
        acc.zipWith(stm)((xs, x) => x :: xs)
    }

  /**
   * Kills the fiber running the effect.
   */
  final def die(t: Throwable): STM[Nothing, Nothing] =
    succeedLazy(throw t)

  /**
   * Kills the fiber running the effect with a `RuntimeException` that contains
   * the specified message.
   */
  final def dieMessage(m: String): STM[Nothing, Nothing] =
    die(new RuntimeException(m))

  /**
   * Returns a value that models failure in the transaction.
   */
  final def fail[E](e: E): STM[E, Nothing] = new STM(_ => TRez.Fail(e))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces a new `List[B]`.
   */
  final def foreach[E, A, B](as: Iterable[A])(f: A => STM[E, B]): STM[E, List[B]] =
    collectAll(as.map(f))

  /**
   * Creates an STM effect from an `Either` value.
   */
  final def fromEither[E, A](e: Either[E, A]): STM[E, A] =
    STM.succeedLazy {
      e match {
        case Left(t)  => STM.fail(t)
        case Right(a) => STM.succeed(a)
      }
    }.flatten

  /**
   * Creates an STM effect from a `Try` value.
   */
  final def fromTry[A](a: => Try[A]): STM[Throwable, A] =
    STM.succeedLazy {
      try a match {
        case Failure(t) => STM.fail(t)
        case Success(a) => STM.succeed(a)
      } catch {
        case t: Throwable => STM.fail(t)
      }
    }.flatten

  /**
   * Creates an `STM` value from a partial (but pure) function.
   */
  final def partial[A](a: => A): STM[Throwable, A] =
    fromTry(Try(a))

  /**
   * Abort and retry the whole transaction when any of the underlying
   * transactional variables have changed.
   */
  final val retry: STM[Nothing, Nothing] =
    new STM(_ => TRez.Retry)

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  final def succeed[A](a: A): STM[Nothing, A] =
    new STM(_ => TRez.Succeed(a))

  /**
   * Returns an `STM` effect that succeeds with the specified (lazily
   * evaluated) value.
   */
  final def succeedLazy[A](a: => A): STM[Nothing, A] =
    new STM(_ => TRez.Succeed(a))

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  final val unit: STM[Nothing, Unit] = succeed(())
}

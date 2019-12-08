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

package zio.stm

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong }

import zio.{ Fiber, IO, UIO }
import zio.internal.{ Platform, Stack, Sync }
import java.util.{ HashMap => MutableMap }

import com.github.ghik.silencer.silent

import scala.util.{ Failure, Success, Try }
import scala.annotation.tailrec

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
 *
 * Software Transactional Memory is a technique which allows composition
 *  of arbitrary atomic operations. It is the software analog of transactions in database systems.
 *
 * The API is lifted directly from the Haskell package Control.Concurrent.STM although the implementation does not
 *  resemble the Haskell one at all.
 *  [[http://hackage.haskell.org/package/stm-2.5.0.0/docs/Control-Concurrent-STM.html]]
 *
 *  STM in Haskell was introduced in:
 *  Composable memory transactions, by Tim Harris, Simon Marlow, Simon Peyton Jones, and Maurice Herlihy, in ACM
 *  Conference on Principles and Practice of Parallel Programming 2005.
 * [[https://www.microsoft.com/en-us/research/publication/composable-memory-transactions/]]
 *
 * See also:
 * Lock Free Data Structures using STMs in Haskell, by Anthony Discolo, Tim Harris, Simon Marlow, Simon Peyton Jones,
 * Satnam Singh) FLOPS 2006: Eighth International Symposium on Functional and Logic Programming, Fuji Susono, JAPAN,
 *  April 2006
 *  [[https://www.microsoft.com/en-us/research/publication/lock-free-data-structures-using-stms-in-haskell/]]
 *
 */
final class STM[+E, +A] private[stm] (
  private val exec: (STM.internal.Journal, Fiber.Id, AtomicLong) => STM.internal.TRez[E, A]
) extends AnyVal { self =>
  import STM.internal.{ prepareResetJournal, TRez }

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
   * Maps the success value of this effect to the specified constant value.
   */
  final def as[B](b: => B): STM[E, B] = self map (_ => b)

  /**
   * Maps the error value of this effect to the specified constant value.
   */
  final def asError[E1](e: => E1): STM[E1, A] = self mapError (_ => e)

  /**
   * Simultaneously filters and maps the value produced by this effect.
   */
  final def collect[B](pf: PartialFunction[A, B]): STM[E, B] =
    collectM(pf.andThen(STM.succeed(_)))

  /**
   * Simultaneously filters and flatMaps the value produced by this effect.
   * Continues on the effect returned from pf.
   */
  final def collectM[E1 >: E, B](pf: PartialFunction[A, STM[E1, B]]): STM[E1, B] =
    new STM(
      (journal, fiberId, stackSize) =>
        continueWith(journal, fiberId, stackSize) {
          case t @ TRez.Fail(_) => t
          case TRez.Succeed(a)  => if (pf.isDefinedAt(a)) pf(a).exec(journal, fiberId) else TRez.Retry
          case TRez.Retry       => TRez.Retry
        }
    )

  /**
   * Commits this transaction atomically.
   */
  final def commit: IO[E, A] = STM.atomically(self)

  /**
   * Converts the failure channel into an `Either`.
   */
  final def either: STM[Nothing, Either[E, A]] = fold(Left(_), Right(_))

  /**
   * Executes the specified finalization transaction whether or
   * not this effect succeeds. Note that as with all STM transactions,
   * if the full transaction fails, everything will be rolled back.
   */
  final def ensuring(finalizer: STM[Nothing, Any]): STM[E, A] =
    foldM(e => finalizer *> STM.fail(e), a => finalizer *> STM.succeed(a))

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
      (journal, fiberId, stackSize) =>
        continueWith(journal, fiberId, stackSize) {
          case TRez.Succeed(a)  => f(a).exec(journal, fiberId, stackSize)
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
      (journal, fiberId, stackSize) =>
        continueWith(journal, fiberId, stackSize) {
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
      (journal, fiberId, stackSize) =>
        continueWith(journal, fiberId, stackSize) {
          case TRez.Fail(e)    => f(e).exec(journal, fiberId, stackSize)
          case TRez.Succeed(a) => g(a).exec(journal, fiberId, stackSize)
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Returns a new effect that ignores the success or failure of this effect.
   */
  final def ignore: STM[Nothing, Unit] = self.either.unit

  /**
   * Maps the value produced by the effect.
   */
  final def map[B](f: A => B): STM[E, B] =
    new STM(
      (journal, fiberId, stackSize) =>
        continueWith(journal, fiberId, stackSize) {
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
      (journal, fiberId, stackSize) =>
        continueWith(journal, fiberId, stackSize) {
          case t @ TRez.Succeed(_) => t
          case TRez.Fail(e)        => TRez.Fail(f(e))
          case TRez.Retry          => TRez.Retry
        }
    )

  /**
   * Converts the failure channel into an `Option`.
   */
  final def option: STM[Nothing, Option[A]] = fold(_ => None, Some(_))

  /**
   * Tries this effect first, and if it fails, tries the other effect.
   */
  final def orElse[E1, A1 >: A](that: => STM[E1, A1]): STM[E1, A1] =
    new STM(
      (journal, fiberId, stackSize) => {
        val reset = prepareResetJournal(journal)

        continueWith(journal, fiberId, stackSize) {
          case TRez.Fail(_)        => { reset(); that.exec(journal, fiberId, stackSize) }
          case t @ TRez.Succeed(_) => t
          case TRez.Retry          => { reset(); that.exec(journal, fiberId, stackSize) }
        }
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
  final def unit: STM[E, Unit] = as(())

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

  private def continueWith[E1, B](journal: STM.internal.Journal, fiberId: Fiber.Id, stackSize: AtomicLong)(
    continue: TRez[E, A] => TRez[E1, B]
  ): TRez[E1, B] = {
    val framesCount = stackSize.incrementAndGet()

    if (framesCount > STM.MaxFrames)
      throw new STM.Resumable(self, continue)
    else
      continue(self.exec(journal, fiberId, stackSize))
  }

  private def run(journal: STM.internal.Journal, fiberId: Fiber.Id): TRez[E, A] = {
    type Cont = Any => TRez[Any, Any]

    val stackSize = new AtomicLong()
    val stack     = Stack[Cont]()
    var current   = self.asInstanceOf[STM[Any, Any]]
    var result    = null: AnyRef

    while (result eq null) {
      try {
        val v = current.exec(journal, fiberId, stackSize).asInstanceOf[AnyRef]

        if (stack.isEmpty)
          result = v
        else {
          val next = stack.pop()
          current = new STM((_, _, _) => next(v))
        }
      } catch {
        case cont: STM.Resumable[_, _, _, _] =>
          current = cont.stm
          stack.push(cont.f.asInstanceOf[Cont])
          stackSize.set(0)
      }
    }

    result.asInstanceOf[TRez[E, A]]
  }
}

object STM {

  private final class Resumable[E, E1, A, B](val stm: STM[E, A], val f: internal.TRez[E, A] => internal.TRez[E1, B])
      extends Throwable(null, null, false, false)

  private final val MaxFrames = 200

  private[stm] object internal {
    final val DefaultJournalSize = 4

    class Versioned[A](val value: A)

    type TxnId = Long

    type Journal =
      MutableMap[TRef[_], STM.internal.Entry]

    type Todo = () => Unit

    /**
     * Creates a function that can reset the journal.
     */
    final def prepareResetJournal(journal: Journal): () => Unit = {
      val saved = new MutableMap[TRef[_], Entry](journal.size)

      val it = journal.entrySet.iterator
      while (it.hasNext) {
        val entry = it.next
        saved.put(entry.getKey, entry.getValue.copy())
      }

      () => { journal.clear(); journal.putAll(saved); () }
    }

    /**
     * Commits the journal.
     */
    final def commitJournal(journal: Journal): Unit = {
      val it = journal.entrySet.iterator
      while (it.hasNext) it.next.getValue.commit()
    }

    /**
     * Allocates memory for the journal, if it is null, otherwise just clears it.
     */
    final def allocJournal(journal: Journal): Journal =
      if (journal eq null) new MutableMap[TRef[_], Entry](DefaultJournalSize)
      else {
        journal.clear()
        journal
      }

    /**
     * Determines if the journal is valid.
     */
    final def isValid(journal: Journal): Boolean = {
      var valid = true
      val it    = journal.entrySet.iterator
      while (valid && it.hasNext) valid = it.next.getValue.isValid
      valid
    }

    /**
     * Analyzes the journal, determining whether it is valid and whether it is
     * read only in a single pass. Note that information on whether the
     * journal is read only will only be accurate if the journal is valid, due
     * to short-circuiting that occurs on an invalid journal.
     */
    final def analyzeJournal(journal: Journal): JournalAnalysis = {
      var result = JournalAnalysis.ReadOnly: JournalAnalysis
      val it     = journal.entrySet.iterator
      while ((result ne JournalAnalysis.Invalid) && it.hasNext) {
        val value = it.next.getValue
        if (value.isInvalid) result = JournalAnalysis.Invalid
        else if (value.isChanged) result = JournalAnalysis.ReadWrite
      }
      result
    }

    sealed trait JournalAnalysis extends Serializable with Product
    object JournalAnalysis {
      case object Invalid   extends JournalAnalysis
      case object ReadOnly  extends JournalAnalysis
      case object ReadWrite extends JournalAnalysis
    }

    /**
     * Determines if the journal is invalid.
     */
    final def isInvalid(journal: Journal): Boolean = !isValid(journal)

    /**
     * Atomically collects and clears all the todos from any `TRef` that
     * participated in the transaction.
     */
    final def collectTodos(journal: Journal): MutableMap[TxnId, Todo] = {
      import collection.JavaConverters._

      val allTodos  = new MutableMap[TxnId, Todo](DefaultJournalSize)
      val emptyTodo = Map.empty[TxnId, Todo]

      val it = journal.entrySet.iterator
      while (it.hasNext) {
        val tref = it.next.getValue.tref
        val todo = tref.todo

        var loop = true
        while (loop) {
          val oldTodo = todo.get

          loop = !todo.compareAndSet(oldTodo, emptyTodo)

          if (!loop) allTodos.putAll(oldTodo.asJava): @silent("JavaConverters")
        }
      }

      allTodos
    }

    /**
     * Executes the todos in the current thread, sequentially.
     */
    final def execTodos(todos: MutableMap[TxnId, Todo]): Unit = {
      val it = todos.entrySet.iterator
      while (it.hasNext) it.next.getValue.apply()
    }

    /**
     * For the given transaction id, adds the specified todo effect to all
     * `TRef` values.
     */
    final def addTodo(txnId: TxnId, journal: Journal, todoEffect: Todo): Boolean = {
      var added = false

      val it = journal.entrySet.iterator
      while (it.hasNext) {
        val tref = it.next.getValue.tref

        var loop = true
        while (loop) {
          val oldTodo = tref.todo.get

          if (!oldTodo.contains(txnId)) {
            val newTodo = oldTodo.updated(txnId, todoEffect)

            loop = !tref.todo.compareAndSet(oldTodo, newTodo)

            if (!loop) added = true
          } else loop = false
        }
      }

      added
    }

    /**
     * Runs all the todos.
     */
    final def completeTodos[E, A](io: IO[E, A], journal: Journal, platform: Platform): TryCommit[E, A] = {
      val todos = collectTodos(journal)

      if (todos.size > 0) platform.executor.submitOrThrow(() => execTodos(todos))

      TryCommit.Done(io)
    }

    /**
     * Finds all the new todo targets that are not already tracked in the `oldJournal`.
     */
    final def untrackedTodoTargets(oldJournal: Journal, newJournal: Journal): Journal = {
      val untracked = new MutableMap[TRef[_], Entry](newJournal.size)

      untracked.putAll(newJournal)

      val it = newJournal.entrySet.iterator
      while (it.hasNext) {
        val entry = it.next
        val key   = entry.getKey
        val value = entry.getValue
        if (oldJournal.containsKey(key)) {
          // We already tracked this one, remove it:
          untracked.remove(key)
        } else if (value.isNew) {
          // This `TRef` was created in the current transaction, so no need to
          // add any todos to it, because it cannot be modified from the outside
          // until the transaction succeeds; so any todo added to it would never
          // succeed.
          untracked.remove(key)
        }
      }

      untracked
    }

    final def tryCommitAsync[E, A](
      journal: Journal,
      platform: Platform,
      fiberId: Fiber.Id,
      stm: STM[E, A],
      txnId: TxnId,
      done: AtomicBoolean
    )(
      k: IO[E, A] => Unit
    ): Unit = {
      def complete(io: IO[E, A]): Unit = { done.set(true); k(io) }

      @tailrec
      def suspend(accum: Journal, journal: Journal): Unit = {
        addTodo(txnId, journal, () => tryCommitAsync(null, platform, fiberId, stm, txnId, done)(k))

        if (isInvalid(journal)) tryCommit(platform, fiberId, stm) match {
          case TryCommit.Done(io) => complete(io)
          case TryCommit.Suspend(journal2) =>
            val untracked = untrackedTodoTargets(accum, journal2)

            if (untracked.size > 0) {
              accum.putAll(untracked)

              suspend(accum, untracked)
            }
        }
      }

      Sync(done) {
        if (!done.get) {
          if (journal ne null) suspend(journal, journal)
          else
            tryCommit(platform, fiberId, stm) match {
              case TryCommit.Done(io)         => complete(io)
              case TryCommit.Suspend(journal) => suspend(journal, journal)
            }
        }
      }
    }

    final def tryCommit[E, A](platform: Platform, fiberId: Fiber.Id, stm: STM[E, A]): TryCommit[E, A] = {
      var journal = null.asInstanceOf[MutableMap[TRef[_], Entry]]
      var value   = null.asInstanceOf[TRez[E, A]]

      var loop = true

      while (loop) {
        journal = allocJournal(journal)
        value = stm.run(journal, fiberId)

        val analysis = analyzeJournal(journal)

        if (analysis ne JournalAnalysis.Invalid) {
          loop = false

          value match {
            case _: TRez.Succeed[_] =>
              if (analysis eq JournalAnalysis.ReadWrite) {
                Sync(globalLock) {
                  if (isValid(journal)) commitJournal(journal) else loop = true
                }
              } else {
                Sync(globalLock) {
                  if (isInvalid(journal)) loop = true
                }
              }

            case _ =>
          }
        }
      }

      value match {
        case TRez.Succeed(a) => completeTodos(IO.succeed(a), journal, platform)
        case TRez.Fail(e)    => completeTodos(IO.fail(e), journal, platform)
        case TRez.Retry      => TryCommit.Suspend(journal)
      }
    }

    final val succeedUnit: TRez[Nothing, Unit] = TRez.Succeed(())

    final def makeTxnId(): Long = txnCounter.incrementAndGet()

    private[this] val txnCounter: AtomicLong = new AtomicLong()

    final val globalLock = new AnyRef {}

    sealed trait TRez[+A, +B] extends Serializable with Product
    object TRez {
      final case class Fail[A](value: A)    extends TRez[A, Nothing]
      final case class Succeed[B](value: B) extends TRez[Nothing, B]
      case object Retry                     extends TRez[Nothing, Nothing]
    }

    abstract class Entry { self =>
      type A

      val tref: TRef[A]

      protected[this] val expected: Versioned[A]
      protected[this] var newValue: A

      val isNew: Boolean

      private[this] var _isChanged = false

      final def unsafeSet(value: Any): Unit = {
        _isChanged = true
        newValue = value.asInstanceOf[A]
      }

      final def unsafeGet[B]: B = newValue.asInstanceOf[B]

      /**
       * Commits the new value to the `TRef`.
       */
      final def commit(): Unit = tref.versioned = new Versioned(newValue)

      /**
       * Creates a copy of the Entry.
       */
      final def copy(): Entry = new Entry {
        type A = self.A
        val tref     = self.tref
        val expected = self.expected
        val isNew    = self.isNew
        var newValue = self.newValue
        _isChanged = self.isChanged
      }

      /**
       * Determines if the entry is invalid. This is the negated version of
       * `isValid`.
       */
      final def isInvalid: Boolean = !isValid

      /**
       * Determines if the entry is valid. That is, if the version of the
       * `TRef` is equal to the expected version.
       */
      final def isValid: Boolean = tref.versioned eq expected

      /**
       * Determines if the variable has been set in a transaction.
       */
      final def isChanged: Boolean = _isChanged

      override def toString: String =
        s"Entry(expected.value = ${expected.value}, newValue = $newValue, tref = $tref, isChanged = $isChanged)"
    }

    object Entry {

      /**
       * Creates an entry for the journal, given the `TRef` being untracked, the
       * new value of the `TRef`, and the expected version of the `TRef`.
       */
      final def apply[A0](tref0: TRef[A0], isNew0: Boolean): Entry = {
        val versioned = tref0.versioned

        new Entry {
          type A = A0
          val tref     = tref0
          val isNew    = isNew0
          val expected = versioned
          var newValue = versioned.value
        }
      }
    }

    sealed abstract class TryCommit[+E, +A]
    object TryCommit {
      final case class Done[+E, +A](io: IO[E, A]) extends TryCommit[E, A]
      final case class Suspend(journal: Journal)  extends TryCommit[Nothing, Nothing]
    }
  }

  import internal._

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  final def atomically[E, A](stm: STM[E, A]): IO[E, A] =
    IO.effectSuspendTotalWith { (platform, fiberId) =>
      tryCommit(platform, fiberId, stm) match {
        case TryCommit.Done(io) => io // TODO: Interruptible in Suspend
        case TryCommit.Suspend(journal) =>
          val txnId     = makeTxnId()
          val done      = new AtomicBoolean(false)
          val interrupt = UIO(Sync(done) { done.set(true) })
          val async     = IO.effectAsync[E, A](tryCommitAsync(journal, platform, fiberId, stm, txnId, done))

          async ensuring interrupt
      }
    }

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  final def check(p: Boolean): STM[Nothing, Unit] = if (p) STM.unit else retry

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
  final def die(t: Throwable): STM[Nothing, Nothing] = succeed(throw t)

  /**
   * Kills the fiber running the effect with a `RuntimeException` that contains
   * the specified message.
   */
  final def dieMessage(m: String): STM[Nothing, Nothing] = die(new RuntimeException(m))

  /**
   * Returns a value that models failure in the transaction.
   */
  final def fail[E](e: E): STM[E, Nothing] = new STM((_, _, _) => TRez.Fail(e))

  /**
   * Returns the fiber id of the fiber committing the transaction.
   */
  final val fiberId: STM[Nothing, Fiber.Id] = new STM((_, fiberId, _) => TRez.Succeed(fiberId))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces a new `List[B]`.
   */
  final def foreach[E, A, B](as: Iterable[A])(f: A => STM[E, B]): STM[E, List[B]] =
    collectAll(as.map(f))

  /**
   * Creates an STM effect from an `Either` value.
   */
  final def fromEither[E, A](e: => Either[E, A]): STM[E, A] =
    STM.suspend {
      e match {
        case Left(t)  => STM.fail(t)
        case Right(a) => STM.succeed(a)
      }
    }

  /**
   * Creates an STM effect from a `Try` value.
   */
  final def fromTry[A](a: => Try[A]): STM[Throwable, A] =
    STM.suspend {
      Try(a).flatten match {
        case Failure(t) => STM.fail(t)
        case Success(a) => STM.succeed(a)
      }
    }

  /**
   * Creates an `STM` value from a partial (but pure) function.
   */
  final def partial[A](a: => A): STM[Throwable, A] = fromTry(Try(a))

  /**
   * Abort and retry the whole transaction when any of the underlying
   * transactional variables have changed.
   */
  final val retry: STM[Nothing, Nothing] = new STM((_, _, _) => TRez.Retry)

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  final def succeed[A](a: A): STM[Nothing, A] = new STM((_, _, _) => TRez.Succeed(a))

  /**
   * Suspends creation of the specified transaction lazily.
   */
  final def suspend[E, A](stm: => STM[E, A]): STM[E, A] =
    STM.succeed(stm).flatten

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  final val unit: STM[Nothing, Unit] = succeed(())
}

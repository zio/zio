package scalaz.zio

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable.{ Map => MutableMap }

/**
 * `STM[E, A]` represents a computation that can be performed transactional
 * resulting in a failure `E` or a value `A`.
 *
 * {{{
 * def transfer(receiver: TVar[Int],
 *              sender: TVar[Int], much: Int): UIO[Int] =
 *   STM.atomically {
 *     for {
 *       balance <- sender.get
 *       _       <- check(balance >= much)
 *       _       <- receiver.update(_ + much)
 *       _       <- sender.update(_ - much)
 *       newAmnt <- receiver.get
 *     } yield newAmnt
 *   }
 *
 *   val action: UIO[Int] =
 *     for {
 *       t <- STM.atomically(TVar.make(0).zip(TVar.make(20000)))
 *       (receiver, sender) = t
 *       balance <- transfer(receiver, sender, 1000)
 *     } yield balance
 * }}}
 */
final class STM[+E, +A] private (
  val run: STM.internal.Journal => STM.internal.TRez[E, A]
) extends AnyVal { self =>
  import STM.internal.TRez

  /**
   * Converts the failure channel into an `Either`.
   */
  final def either: STM[Nothing, Either[E, A]] =
    new STM(
      journal =>
        (self run journal) match {
          case TRez.Fail(e)    => TRez.Succeed(Left(e))
          case TRez.Succeed(a) => TRez.Succeed(Right(a))
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Feeds the value produced by this computation to the specified computation,
   * and then runs that computation as well to produce its failure or value.
   */
  final def flatMap[E1 >: E, B](f: A => STM[E1, B]): STM[E1, B] =
    new STM(
      journal =>
        (self run journal) match {
          case TRez.Succeed(a)  => f(a) run journal
          case t @ TRez.Fail(_) => t
          case TRez.Retry       => TRez.Retry
        }
    )

  /**
   * Folds over the `STM` effect, handling both failure and success.
   */
  final def fold[B](f: E => B, g: A => B): STM[Nothing, B] =
    new STM(
      journal =>
        (self run journal) match {
          case TRez.Fail(e)    => TRez.Succeed(f(e))
          case TRez.Succeed(a) => TRez.Succeed(g(a))
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Effectfully folds over the `STM` effect, handling both failure and success.
   */
  final def foldM[E1, B](f: E => STM[E1, B], g: A => STM[E1, B]): STM[E1, B] =
    new STM(
      journal =>
        (self run journal) match {
          case TRez.Fail(e)    => f(e) run journal
          case TRez.Succeed(a) => g(a) run journal
          case TRez.Retry      => TRez.Retry
        }
    )

  /**
   * Maps the value produced by the computation.
   */
  final def map[B](f: A => B): STM[E, B] =
    new STM(
      journal =>
        self.run(journal) match {
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
        self.run(journal) match {
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
   * Tries this computation first, and if it fails, tries the other computation.
   */
  final def orElse[E1, A1 >: A](that: => STM[E1, A1]): STM[E1, A1] =
    self.foldM(_ => that, STM.succeed(_))

  /**
   * Sequentially zips this value with the specified one.
   */
  final def ~[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    self zip that

  /**
   * Sequentially zips this value with the specified one.
   */
  final def zip[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    (self zipWith that)((a, b) => a -> b)

  /**
   * Sequentially zips this value with the specified one, combining the values
   * using the specified combiner function.
   */
  final def zipWith[E1 >: E, B, C](that: => STM[E1, B])(f: (A, B) => C): STM[E1, C] =
    self.flatMap(a => that.map(b => f(a, b)))
}

object STM {

  private[STM] object internal {
    class Versioned[A](val value: A)

    type Journal =
      MutableMap[Long, STM.internal.Entry]

    /**
     * Atomically collects and clears all the todos from any `TVar` that
     * participated in the transaction. This is not a pure function, despite
     * the return type (it effectfully clears todos from `TVar` values).
     */
    final def collectTodos(journal: Journal): UIO[Any] =
      journal.values.foldLeft[UIO[Any]](UIO.unit) {
        case (acc0, entry) =>
          val todo          = entry.tvar.todo
          var acc: UIO[Any] = acc0

          var loop = true
          while (loop) {
            val oldTodo = todo.get

            val newTodo = Map.empty[Long, UIO[_]]

            loop = !todo.compareAndSet(oldTodo, newTodo)

            if (!loop) {
              acc = oldTodo.values.foldLeft(acc)(_ *> _)
            }
          }

          acc
      }

    final def succeedUnit[A]: TRez[A, Unit] =
      _SucceedUnit.asInstanceOf[TRez[A, Unit]]

    private[this] val _SucceedUnit: TRez[Any, Unit] = TRez.Succeed(())

    final def makeTVarId(): Long = tvarCounter.incrementAndGet()

    final def makeTxnId(): Long = txnCounter.incrementAndGet()

    private[this] val tvarCounter: AtomicLong = new AtomicLong()

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
      val tvar: TVar[A]
      var newValue: A
      val expected: Versioned[A]

      final def unsafeSet(value: Any): Unit =
        newValue = value.asInstanceOf[A]

      final def unsafeGet[B]: B =
        newValue.asInstanceOf[B]

      /**
       * Determines if the entry is valid. That is, if the version of the
       * `TVar` is equal to the expected version.
       */
      final def isValid: Boolean =
        tvar.versioned eq expected

      /**
       * Commits the new value to the `TVar`.
       */
      final def commit(): Unit =
        tvar.versioned = new Versioned(newValue)
    }

    object Entry {

      /**
       * Creates an entry for the journal, given the `TVar` being updated, the
       * new value of the `TVar`, and the expected version of the `TVar`.
       */
      def apply[A0](tvar0: TVar[A0], newValue0: A0, expected0: Versioned[A0]): Entry =
        new Entry {
          type A = A0
          val tvar     = tvar0
          var newValue = newValue0
          val expected = expected0
        }
    }
  }

  import internal._

  /**
   * A variable that can be modified as part of a transactional computation.
   */
  class TVar[A] private (
    val id: Long,
    @volatile var versioned: Versioned[A],
    val todo: AtomicReference[Map[Long, UIO[_]]]
  ) {
    self =>

    /**
     * Retrieves the value of the `TVar`.
     */
    final val get: STM[Nothing, A] =
      new STM(journal => {
        val entry = getOrMakeEntry(journal)

        TRez.Succeed(entry.unsafeGet[A])
      })

    /**
     * Sets the value of the `tvar`.
     */
    final def set(newValue: A): STM[Nothing, Unit] =
      new STM(journal => {
        val entry = getOrMakeEntry(journal)

        entry unsafeSet newValue

        succeedUnit
      })

    /**
     * Updates the value of the variable.
     */
    final def update(f: A => A): STM[Nothing, A] =
      new STM(journal => {
        val entry = getOrMakeEntry(journal)

        val newValue = f(entry.unsafeGet[A])

        entry unsafeSet newValue

        TRez.Succeed(newValue)
      })

    private def getOrMakeEntry(journal: Journal): Entry =
      if (journal contains id) journal(id)
      else {
        val expected = versioned
        val entry    = Entry(self, expected.value, expected)
        journal.update(id, entry)
        entry
      }
  }

  object TVar {

    /**
     * Makes a new `TVar`.
     */
    final def make[A](a: => A): STM[Nothing, TVar[A]] =
      new STM(journal => {
        val id = makeTVarId()

        val value     = a
        val versioned = new Versioned(value)

        val todo = new AtomicReference[Map[Long, UIO[_]]](Map())

        val tvar = new TVar(id, versioned, todo)

        journal.update(id, Entry(tvar, value, versioned))

        TRez.Succeed(tvar)
      })
  }

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

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  final def atomically[E, A](stm: STM[E, A]): IO[E, A] =
    IO.effectAsyncMaybe[E, A] { k =>
      import internal.globalLock

      val txnId = makeTxnId()

      val done = new AtomicBoolean(false)

      def tryTransaction(onTodo: Boolean): Option[IO[E, A]] =
        done.synchronized {
          if (done.get) None
          else {
            var journal = null.asInstanceOf[MutableMap[Long, Entry]]
            var value   = null.asInstanceOf[TRez[E, A]]

            var loop = true

            while (loop) {
              journal = MutableMap.empty[Long, Entry]
              value = stm run journal

              if (value != TRez.Retry) {
                done set true

                try {
                  globalLock.acquire()

                  if (journal.values forall (_.isValid)) {
                    journal.values foreach (_.commit())

                    loop = false
                  }
                } finally globalLock.release()
              } else {
                val tryLater: UIO[Unit] =
                  UIO.effectTotal(tryLaterImpure())

                journal.values.foreach { entry =>
                  val tvar = entry.tvar

                  var loop = true
                  while (loop) {
                    val oldTodo = tvar.todo.get

                    val newTodo = oldTodo + (txnId -> tryLater)

                    loop = !tvar.todo.compareAndSet(oldTodo, newTodo)
                  }
                }

                loop = false
              }
            }

            value match {
              case TRez.Succeed(a) =>
                Some(collectTodos(journal).fork *> IO.succeed(a))

              case TRez.Fail(e) =>
                Some(collectTodos(journal).fork *> IO.fail(e))

              case TRez.Retry =>
                if (!onTodo) tryTransaction(true)
                else None
            }
          }
        }

      def tryLaterImpure(): Unit =
        tryTransaction(false) match {
          case None     =>
          case Some(io) => k(io)
        }

      tryTransaction(false)
    }

  /**
   * Abort and retry the whole transaction when any of the underlying
   * variables have changed.
   */
  final val retry: STM[Nothing, Nothing] =
    new STM(_ => TRez.Retry)

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  final def check(p: Boolean): STM[Nothing, Unit] =
    if (p) STM.unit else retry

  /**
   * Returns a value that models failure in the transaction.
   */
  final def fail[E](e: E): STM[E, Nothing] = new STM(_ => TRez.Fail(e))
}

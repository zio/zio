package scalaz.zio

import java.util.concurrent.atomic.AtomicLong

/**
 * `STM[E, A]` represents a computation that can be performed transactional
 * resulting in a failure `E` or a value `A`.
 *
 * {{{
 * def transfer(receiver: TVar[Int],
 *              sender: TVar[Int], much: Int): IO[Nothing, Int] =
 *   STM.atomically {
 *     for {
 *       _ <- receiver.update(_ + much)
 *       s <- sender.get
 *       _ <- check(s >= much)
 *       _ <- sender.update(_ - much)
 *       r <- receiver.get
 *     } yield r
 *   }
 *
 *   val action: IO[Nothing, Int] =
 *     for {
 *       t <- STM.atomically(TVar(0).seq(TVar(20000)))
 *       (receiver, sender) = t
 *       balance <- transfer(receiver, sender, 1000)
 *     } yield balance
 * }}}
 */
final case class STM[+E, +A] private (
  run: Map[Long, STM.internal.Entry] => (Map[Long, STM.internal.Entry], Either[E, A])
) { self =>

  /**
   * Maps from one error type to another.
   */
  def leftMap[E2](f: E => E2): STM[E2, A] =
    STM(
      journal =>
        self.run(journal) match {
          case (journal, either) => {
            val leftMapped: Either[A, E2] = either.swap match {
              case Right(e) => Right(f(e))
              case Left(a)  => Left(a)
            }
            (journal, leftMapped.swap)
          }
      }
    )

  /**
   * Maps the value produced by the computation.
   */
  def map[B](f: A => B): STM[E, B] =
    STM(
      journal =>
        self.run(journal) match {
          case (journal, either) => {
            val eitherMapped = either match {
              case Right(a) => Right(f(a))
              case Left(b)  => Left(b)
            }
            (journal, eitherMapped)
          }
      }
    )

  /**
   * Feeds the value produced by this computation to the specified computation,
   * and then runs that computation as well to produce its failure or value.
   */
  def flatMap[E1 >: E, B](f: A => STM[E1, B]): STM[E1, B] =
    STM(
      journal =>
        self.run(journal) match {
          case (journal, Left(error)) => (journal, Left(error))
          case (journal, Right(a))    => f(a).run(journal)
      }
    )

  /**
   * Tries this computation first, and if it fails, tries the other computation.
   */
  def orElse[E2, A1 >: A](that: => STM[E2, A1]): STM[E2, A1] =
    self.attempt.flatMap(either => either.fold(_ => that, STM.point(_)))

  /**
   * Surfaces any errors at the value level, where they can be recovered from.
   */
  def attempt: STM[Nothing, Either[E, A]] =
    STM(
      journal =>
        self.run(journal) match {
          case (journal, value) => (journal, Right(value))
      }
    )

  /**
   * Sequentially zips this value with the specified one.
   */
  def seq[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    self.flatMap(a => that.map(b => (a, b)))
}

object STM {

  object internal {
    def freshIdentity(): Long = counter.incrementAndGet()

    private val counter: AtomicLong = new AtomicLong()

    val semaphore = new java.util.concurrent.Semaphore(1)

    abstract class Entry {
      type A
      val tVar: TVar[A]
      val newValue: A
      val expectedVersion: Long

      def isValid: Boolean = tVar.version == expectedVersion
      def commit(): Unit   = tVar.value = newValue
    }

    object Entry {

      def apply[A0](tVar0: TVar[A0], newValue0: A0, expectedVersion0: Long): Entry = new Entry {
        type A = A0
        val tVar            = tVar0
        val newValue        = newValue0
        val expectedVersion = expectedVersion0
      }
    }
  }

  import internal._

  /**
   * A variable that can be modified as part of a transactional computation.
   */
  class TVar[A] private (identity: Long, @volatile var version: Long, @volatile var value: A) {
    self =>

    final def get: STM[Nothing, A] =
      STM(journal => {
        val entry = journal.getOrElse(identity, Entry(self, value, version))

        (journal.updated(identity, entry), Right(value))
      })

    final def set(newValue: A): STM[Nothing, Unit] =
      STM(journal => {
        val entry = journal.getOrElse(identity, Entry(self, newValue, version))

        (journal.updated(identity, entry), Right(()))
      })

    final def update(f: A => A): STM[Nothing, Unit] =
      get.flatMap(a => set(f(a)))
  }

  object TVar {
    final def apply[A](initialValue: => A): STM[Nothing, TVar[A]] =
      STM(journal => {
        val identity = freshIdentity()

        val tVar = new TVar(identity, 0, initialValue)

        (journal.updated(identity, Entry(tVar, initialValue, 0)), Right(tVar))
      })
  }

  final def point[A](a: => A): STM[Nothing, A] =
    STM(journal => (journal, Right(a)))

  final def atomically[E, A](stm: STM[E, A]): IO[E, A] =
    IO.absolve(IO.sync[Either[E, A]] {
      import internal.semaphore

      var retry = true

      val (journal, value) = stm.run(Map.empty)

      while (retry) {
        try {
          semaphore.acquire()

          if (journal.forall(_._2.isValid)) {
            value match {
              case Right(_) => journal.foreach(_._2.commit())
              case _        =>
            }

            retry = false
          }
        } finally semaphore.release()
      }

      value
    })

  /**
   * Abort and retry the whole transaction when any of the underlying
   * variables have changed.
   */
  final def retry: STM[Nothing, Nothing] = ???

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  final def check(p: Boolean): STM[Nothing, Unit] = if (p) STM.point(()) else retry

  /**
   * Who knows!!!
   */
  final def alwaysSucceeds[E](stm: STM[E, Any]): STM[E, Unit] = ???

  /**
   * Who knows!!!
   */
  final def always[E](stm: STM[E, Boolean]): STM[E, Unit] = ???

  /**
   * Returns a value that models failure in the transaction.
   */
  final def fail[E](e: E): STM[E, Nothing] = STM(journal => (journal, Left(e)))
}

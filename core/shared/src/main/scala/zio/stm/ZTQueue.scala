package zio.stm

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.immutable.{Queue => ScalaQueue}

/**
 * A `ZTQueue[RA, RB, EA, EB, A, B]` is a transactional queue. Offerors can
 * offer values of type `A` to the queue and takers can take values of type `B`
 * from the queue. Offering values can require an environment of type `RA` and
 * fail with an error of type `EA`. Taking values can require an environment of
 * type `RB` and fail with an error of type `EB`.
 */
trait ZTQueue[-RA, -RB, +EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * The maximum capacity of the queue.
   */
  def capacity: Int

  /**
   * Checks whether the queue is shut down.
   */
  def isShutdown: USTM[Boolean]

  /**
   * Offers a value to the queue, returning whether the value was offered to the
   * queue.
   */
  def offer(a: A): ZSTM[RA, EA, Boolean]

  /**
   * Offers all of the specified values to the queue, returning whether they
   * were offered to the queue.
   */
  def offerAll(as: Iterable[A]): ZSTM[RA, EA, Boolean]

  /**
   * Views the next element in the queue without removing it, retrying if the
   * queue is empty.
   */
  def peek: ZSTM[RB, EB, B]

  /**
   * Views the next element in the queue without removing it, returning `None`
   * if the queue is empty.
   */
  def peekOption: ZSTM[RB, EB, Option[B]]

  /**
   * Shuts down the queue.
   */
  def shutdown: USTM[Unit]

  /**
   * The current number of values in the queue.
   */
  def size: USTM[Int]

  /**
   * Takes a value from the queue.
   */
  def take: ZSTM[RB, EB, B]

  /**
   * Takes all the values from the queue.
   */
  def takeAll: ZSTM[RB, EB, Chunk[B]]

  /**
   * Takes up to the specified number of values from the queue.
   */
  def takeUpTo(max: Int): ZSTM[RB, EB, Chunk[B]]

  /**
   * Waits for the queue to be shut down.
   */
  final def awaitShutdown: USTM[Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Transforms values offered to the queue using the specified function.
   */
  final def contramap[C](f: C => A): ZTQueue[RA, RB, EA, EB, C, B] =
    contramapSTM(c => ZSTM.succeedNow(f(c)))

  /**
   * Transforms values offered to the queue using the specified transactional
   * function.
   */
  final def contramapSTM[RC <: RA, EC >: EA, C](f: C => ZSTM[RC, EC, A]): ZTQueue[RC, RB, EC, EB, C, B] =
    dimapSTM(f, ZSTM.succeedNow)

  /**
   * Transforms values offered to and taken from the queue using the specified
   * functions.
   */
  final def dimap[C, D](f: C => A, g: B => D): ZTQueue[RA, RB, EA, EB, C, D] =
    dimapSTM(c => ZSTM.succeedNow(f(c)), b => ZSTM.succeedNow(g(b)))

  /**
   * Transforms messages published to and taken from the queue using the
   * specified transactional functions.
   */
  final def dimapSTM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZSTM[RC, EC, A],
    g: B => ZSTM[RD, ED, D]
  ): ZTQueue[RC, RD, EC, ED, C, D] =
    new ZTQueue[RC, RD, EC, ED, C, D] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(c: C): ZSTM[RC, EC, Boolean] =
        f(c).flatMap(self.offer)
      def offerAll(cs: Iterable[C]): ZSTM[RC, EC, Boolean] =
        ZSTM.foreach(cs)(f).flatMap(self.offerAll)
      def peek: ZSTM[RD, ED, D] =
        self.peek.flatMap(g)
      def peekOption: ZSTM[RD, ED, Option[D]] =
        self.peekOption.flatMap {
          case Some(b) => g(b).asSome
          case None    => ZSTM.none
        }
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[RD, ED, D] =
        self.take.flatMap(g)
      def takeAll: ZSTM[RD, ED, Chunk[D]] =
        self.takeAll.flatMap(bs => ZSTM.foreach(bs)(g))
      def takeUpTo(max: Int): ZSTM[RD, ED, Chunk[D]] =
        self.takeUpTo(max).flatMap(bs => ZSTM.foreach(bs)(g))
    }

  /**
   * Filters values offered to the queue using the specified function.
   */
  final def filterInput[A1 <: A](f: A1 => Boolean): ZTQueue[RA, RB, EA, EB, A1, B] =
    filterInputSTM(a => ZSTM.succeedNow(f(a)))

  /**
   * Filters values offered to the queue using the specified transactional
   * function.
   */
  final def filterInputSTM[RA1 <: RA, EA1 >: EA, A1 <: A](
    f: A1 => ZSTM[RA1, EA1, Boolean]
  ): ZTQueue[RA1, RB, EA1, EB, A1, B] =
    new ZTQueue[RA1, RB, EA1, EB, A1, B] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(a: A1): ZSTM[RA1, EA1, Boolean] =
        f(a).flatMap(b => if (b) self.offer(a) else ZSTM.succeedNow(false))
      def offerAll(as: Iterable[A1]): ZSTM[RA1, EA1, Boolean] =
        ZSTM.filter(as)(f).flatMap(as => if (as.nonEmpty) self.offerAll(as) else ZSTM.succeedNow(false))
      def peek: ZSTM[RB, EB, B] =
        self.peek
      def peekOption: ZSTM[RB, EB, Option[B]] =
        self.peekOption
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[RB, EB, B] =
        self.take
      def takeAll: ZSTM[RB, EB, Chunk[B]] =
        self.takeAll
      def takeUpTo(max: Int): ZSTM[RB, EB, Chunk[B]] =
        self.takeUpTo(max)
    }

  /**
   * Filters values taken from the queue using the specified function.
   */
  final def filterOutput(f: B => Boolean): ZTQueue[RA, RB, EA, EB, A, B] =
    filterOutputSTM(b => ZSTM.succeedNow(f(b)))

  /**
   * Filters values taken from the queue using the specified transactional
   * function.
   */
  final def filterOutputSTM[RB1 <: RB, EB1 >: EB](
    f: B => ZSTM[RB1, EB1, Boolean]
  ): ZTQueue[RA, RB1, EA, EB1, A, B] =
    new ZTQueue[RA, RB1, EA, EB1, A, B] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(a: A): ZSTM[RA, EA, Boolean] =
        self.offer(a)
      def offerAll(as: Iterable[A]): ZSTM[RA, EA, Boolean] =
        self.offerAll(as)
      def peek: ZSTM[RB1, EB1, B] =
        self.peek.flatMap(b => f(b).flatMap(p => if (p) ZSTM.succeedNow(b) else self.take *> peek))
      def peekOption: ZSTM[RB1, EB1, Option[B]] =
        self.peekOption.flatMap {
          case Some(b) => f(b).flatMap(p => if (p) ZSTM.some(b) else self.take *> peekOption)
          case None    => ZSTM.none
        }
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[RB1, EB1, B] =
        self.take.flatMap(b => f(b).flatMap(p => if (p) ZSTM.succeedNow(b) else take))
      def takeAll: ZSTM[RB1, EB1, Chunk[B]] =
        self.takeAll.flatMap(bs => ZSTM.filter(bs)(f))
      def takeUpTo(max: Int): ZSTM[RB1, EB1, Chunk[B]] =
        ZSTM.suspend {
          def loop(max: Int, acc: Chunk[B]): ZSTM[RB1, EB1, Chunk[B]] =
            self.takeUpTo(max).flatMap { bs =>
              if (bs.isEmpty) ZSTM.succeedNow(acc)
              else
                ZSTM.filter(bs)(f).flatMap { filtered =>
                  val length = filtered.length
                  if (length == max) ZSTM.succeedNow(acc ++ filtered)
                  else loop(max - length, acc ++ filtered)
                }
            }
          loop(max, Chunk.empty)
        }
    }

  /**
   * Checks if the queue is empty.
   */
  final def isEmpty: USTM[Boolean] =
    size.map(_ == 0)

  /**
   * Checks if the queue is at capacity.
   */
  final def isFull: USTM[Boolean] =
    size.map(_ == capacity)

  /**
   * Transforms values taken from the queue using the specified function.
   */
  final def map[C](f: B => C): ZTQueue[RA, RB, EA, EB, A, C] =
    mapSTM(b => ZSTM.succeedNow(f(b)))

  /**
   * Transforms values taken from the queue using the specified transactional
   * function.
   */
  final def mapSTM[RC <: RB, EC >: EB, C](f: B => ZSTM[RC, EC, C]): ZTQueue[RA, RC, EA, EC, A, C] =
    dimapSTM(ZSTM.succeedNow, f)

  /**
   * Takes a single element from the queue, returning `None` if the queue is
   * empty.
   */
  final def poll: ZSTM[RB, EB, Option[B]] =
    takeUpTo(1).map(_.headOption)

  /**
   * Drops elements from the queue while they do not satisfy the predicate,
   * taking and returning the first element that does satisfy the predicate.
   * Retries if no elements satisfy the predicate.
   */
  final def seek(f: B => Boolean): ZSTM[RB, EB, B] =
    take.flatMap(b => if (f(b)) ZSTM.succeedNow(b) else seek(f))

  /**
   * Takes a number of elements from the queue between the specified minimum and
   * maximum. If there are fewer than the minimum number of elements available,
   * retries until at least the minimum number of elements have been collected.
   */
  final def takeBetween(min: Int, max: Int): ZSTM[RB, EB, Chunk[B]] =
    ZSTM.suspend {

      def takeRemainder(min: Int, max: Int, acc: Chunk[B]): ZSTM[RB, EB, Chunk[B]] =
        if (max < min) ZSTM.succeedNow(acc)
        else
          takeUpTo(max).flatMap { bs =>
            val remaining = min - bs.length
            if (remaining == 1)
              take.map(b => acc ++ bs :+ b)
            else if (remaining > 1) {
              take.flatMap { b =>
                takeRemainder(remaining - 1, max - bs.length - 1, acc ++ bs :+ b)

              }
            } else
              ZSTM.succeedNow(acc ++ bs)
          }

      takeRemainder(min, max, Chunk.empty)
    }

  /**
   * Takes the specified number of elements from the queue. If there are fewer
   * than the specified number of elements available, it retries until they
   * become available.
   */
  final def takeN(n: Int): ZSTM[RB, EB, Chunk[B]] =
    takeBetween(n, n)
}

object ZTQueue {

  /**
   * Creates a bounded queue with the back pressure strategy. The queue will
   * retain values until they have been taken, applying back pressure to
   * offerors if the queue is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def bounded[A](requestedCapacity: Int): USTM[TQueue[A]] =
    makeQueue(requestedCapacity, Strategy.BackPressure)

  /**
   * Creates a bounded queue with the dropping strategy. The queue will drop new
   * values if the queue is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[A](requestedCapacity: Int): USTM[TQueue[A]] =
    makeQueue(requestedCapacity, Strategy.Dropping)

  /**
   * Creates a bounded queue with the sliding strategy. The queue will add new
   * values and drop old values if the queue is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[A](requestedCapacity: Int): USTM[TQueue[A]] =
    makeQueue(requestedCapacity, Strategy.Sliding)

  /**
   * Creates an unbounded queue.
   */
  def unbounded[A]: USTM[TQueue[A]] =
    makeQueue(Int.MaxValue, Strategy.Dropping)

  /**
   * Creates a queue with the specified strategy.
   */
  private def makeQueue[A](requestedCapacity: Int, strategy: Strategy): USTM[TQueue[A]] =
    TRef.make[ScalaQueue[A]](ScalaQueue.empty).map { ref =>
      unsafeMakeQueue(ref, requestedCapacity, strategy)
    }

  /**
   * Unsafely creates a queue with the specified strategy.
   */
  private def unsafeMakeQueue[A](
    ref: TRef[ScalaQueue[A]],
    requestedCapacity: Int,
    strategy: Strategy
  ): TQueue[A] =
    new TQueue[A] {
      val capacity: Int =
        requestedCapacity
      val isShutdown: USTM[Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val queue = ref.unsafeGet(journal)
          queue eq null
        }
      def offer(a: A): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else if (queue.size < capacity) {
            ref.unsafeSet(journal, queue.enqueue(a))
            true
          } else
            strategy match {
              case Strategy.BackPressure => throw ZSTM.RetryException
              case Strategy.Dropping     => false
              case Strategy.Sliding =>
                queue.dequeueOption match {
                  case Some((_, queue)) =>
                    ref.unsafeSet(journal, queue.enqueue(a))
                    true
                  case None =>
                    true
                }
            }
        }
      def offerAll(as: Iterable[A]): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else if (queue.size + as.size <= capacity) {
            ref.unsafeSet(journal, queue ++ as)
            true
          } else
            strategy match {
              case Strategy.BackPressure => throw ZSTM.RetryException
              case Strategy.Dropping =>
                val forQueue = as.take(capacity - queue.size)
                ref.unsafeSet(journal, queue ++ forQueue)
                false
              case Strategy.Sliding =>
                val forQueue = as.take(capacity)
                val toDrop   = queue.size + forQueue.size - capacity
                ref.unsafeSet(journal, queue.drop(toDrop) ++ forQueue)
                true
            }
        }
      val peek: USTM[A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else
            queue.headOption match {
              case Some(a) => a
              case None    => throw ZSTM.RetryException
            }
        }
      val peekOption: USTM[Option[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else queue.headOption
        }
      val shutdown: USTM[Unit] =
        ZSTM.Effect { (journal, _, _) =>
          ref.unsafeSet(journal, null)
        }
      val size: USTM[Int] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else queue.size
        }
      val take: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else
            queue.dequeueOption match {
              case Some((a, queue)) =>
                ref.unsafeSet(journal, queue)
                a
              case None => throw ZSTM.RetryException
            }
        }
      val takeAll: ZSTM[Any, Nothing, zio.Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else {
            ref.unsafeSet(journal, ScalaQueue.empty)
            Chunk.fromIterable(queue)
          }
        }
      def takeUpTo(max: Int): ZSTM[Any, Nothing, Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else {
            val (toTake, remaining) = queue.splitAt(max)
            ref.unsafeSet(journal, remaining)
            Chunk.fromIterable(toTake)
          }
        }
    }

  /**
   * A `Strategy` describes how the queue will handle values if the queue is at
   * capacity.
   */
  private sealed trait Strategy

  private object Strategy {

    /**
     * A strategy that retries if the queue is at capacity.
     */
    case object BackPressure extends Strategy

    /**
     * A strategy that drops new values if the queue is at capacity.
     */
    case object Dropping extends Strategy

    /**
     * A strategy that drops old values if the queue is at capacity.
     */
    case object Sliding extends Strategy
  }
}

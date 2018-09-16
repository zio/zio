package scalaz.ioqueue

// Copyright (C) 2018 John A. De Goes. All rights reserved.

import scala.collection.immutable.{ Queue => IQueue }
import Queue.internal._
import scalaz.zio.{ Fiber, IO, Promise, Ref }

/**
 * A `Queue` is a lightweight, asynchronous queue. This implementation is
 * naive, if functional, and could benefit from significant optimization.
 *
 * TODO:
 *
 * 1. Investigate using a faster option than `Queue`, because `Queue` has
 *    `O(n)` `length` method.
 * 2. Benchmark to see how slow this implementation is and if there are any
 *    easy ways to improve performance.
 */
class Queue[A] private (capacity: Int, ref: Ref[State[A]]) {

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  final def size: IO[Nothing, Int] = ref.get.map(_.size)

  /**
   * Places the value in the queue. If the queue has reached capacity, then
   * the fiber performing the `offer` will be suspended until there is room in
   * the queue.
   */
  final def offer(a: A): IO[Nothing, Unit] = {
    val acquire: (Promise[Nothing, Unit], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        takers.dequeueOption match {
          case None => (p.complete(()), Surplus(IQueue.empty[A].enqueue(a), IQueue.empty))
          case Some((taker, takers)) =>
            (taker.complete(a) *> p.complete(()), Deficit(takers))
        }

      case (p, Surplus(values, putters)) =>
        if (values.length < capacity && putters.isEmpty) {
          (p.complete(()), Surplus(values.enqueue(a), putters))
        } else {
          (IO.now(false), Surplus(values, putters.enqueue((Seq(a), p))))
        }
    }

    val release: (Boolean, Promise[Nothing, Unit]) => IO[Nothing, Unit] = {
      case (_, p) => removePutter(p)
    }

    Promise.bracket[Nothing, State[A], Unit, Boolean](ref)(acquire)(release)
  }

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  final def takeAll: IO[Nothing, List[A]] =
    ref.modify[List[A]] {
      case Surplus(values, putters) => (values.toList, Surplus(IQueue.empty[A], putters))
      case state @ Deficit(_)       => (List.empty[A], state)
    }

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final def take: IO[Nothing, A] = {

    val acquire: (Promise[Nothing, A], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        (IO.now(false), Deficit(takers.enqueue(p)))
      case (p, Surplus(values, putters)) =>
        values.dequeueOption match {
          case None =>
            putters.dequeueOption match {
              case None =>
                (IO.now(false), Deficit(IQueue.empty.enqueue(p)))
              case Some(((a, putter), putters)) if a.tail.isEmpty =>
                (putter.complete(()) *> p.complete(a.head), Surplus(IQueue.empty, putters))
              case Some(((a, putter), putters)) =>
                (p.complete(a.head), Surplus(IQueue.empty, (a.tail, putter) +: putters))
            }
          case Some((a, values)) =>
            (p.complete(a), Surplus(values, putters))
        }
    }

    val release: (Boolean, Promise[Nothing, A]) => IO[Nothing, Unit] = {
      case (_, p) => removeTaker(p)
    }
    Promise.bracket[Nothing, State[A], A, Boolean](ref)(acquire)(release)
  }

  /**
   * Take up to max number of values in the queue. If max > offered, this
   * will return all the elements in the queue without waiting for more offers.
   */
  final def takeUpTo(max: Int): IO[Nothing, List[A]] =
    ref.modify[List[A]] {
      case Surplus(values, putters) =>
        val listA = values.take(max).toList
        val queue = values.drop(max)

        (listA, Surplus(queue, putters))
      case state @ Deficit(_) => (Nil, state)
    }

  /**
   * Interrupts any fibers that are suspended on `take` because the queue is
   * empty. If any fibers are interrupted, returns true, otherwise, returns
   * false.
   */
  final def interruptTake(t: Throwable): IO[Nothing, Boolean] =
    IO.flatten(ref.modify {
      case Deficit(takers) if takers.nonEmpty =>
        val forked: IO[Nothing, Fiber[Nothing, List[Boolean]]] =
          IO.forkAll[Nothing, Boolean](takers.toList.map(_.interrupt(t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(IQueue.empty[Promise[Nothing, A]]))
      case s =>
        (IO.now(false), s)
    })

  /**
   * Interrupts any fibers that are suspended on `offer` because the queue is
   * at capacity. If any fibers are interrupted, returns true, otherwise,
   * returns  false.
   */
  final def interruptOffer(t: Throwable): IO[Nothing, Boolean] =
    IO.flatten(ref.modify {
      case Surplus(_, putters) if putters.nonEmpty =>
        val forked: IO[Nothing, Fiber[Nothing, List[Boolean]]] =
          IO.forkAll[Nothing, Boolean](putters.toList.map(_._2.interrupt(t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(IQueue.empty[Promise[Nothing, A]]))
      case s =>
        (IO.now(false), s)
    })

  final private def removePutter(putter: Promise[Nothing, Unit]): IO[Nothing, Unit] =
    ref.update {
      case Surplus(values, putters) =>
        Surplus(values, putters.filterNot { case (_, p) => p == putter })
      case d => d
    }.toUnit

  final private def removeTaker(taker: Promise[Nothing, A]): IO[Nothing, Unit] =
    ref.update {
      case Deficit(takers) =>
        Deficit(takers.filterNot(_ == taker))

      case d => d
    }.toUnit

  /**
    * Places the value in the queue. If the queue has reached capacity, then
    * the fiber performing the `offer` will be suspended until there is room in
    * the queue.
   */
  final def offerAll(as: Iterable[A]): IO[Nothing, Unit] = {

    def completeAll(
      putterOpt: Option[Promise[Nothing, Unit]],
      deficit: IQueue[(Promise[Nothing, A], A)]
    ): IO[Nothing, Boolean] =
      deficit.dequeueOption match {
        case None                     => putterOpt.fold(IO.now(false))(_.complete(()))
        case Some(((taker, a), rest)) => taker.complete(a) *> completeAll(putterOpt, rest)
      }

    val acquire: (Promise[Nothing, Unit], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        takers.dequeueOption match {
          case None =>
            if (as.size <= capacity)
              p.complete(()) -> Surplus(IQueue.empty[A] ++ as, IQueue.empty)
            else {
              val (addToQueue, surplusValue) = as.splitAt(capacity)
              IO.now(false) ->
                Surplus(
                  IQueue.empty[A] ++ addToQueue,
                  IQueue
                    .empty[(Iterable[A], Promise[Nothing, Unit])]
                    .enqueue(surplusValue -> p)
                )
            }

          case Some(_) =>
            val takersSize = takers.size
            val asSize     = as.size
            if (asSize < takersSize)
              completeAll(Some(p), takers.zip(as)) ->
                Deficit(IQueue.empty ++ takers.drop(asSize))
            else {
              // there are more elements to offer than there are takers.
              val optP = if (asSize > takersSize + capacity) None else Some(p)
              // we serve all the takers and if there is enough remaining capacity
              // in the queue we complete the promise otherwise we queue the promise with
              // the remaining elements to be offered.
              completeAll(optP, takers.zip(as)) ->
                Surplus(
                  IQueue.empty[A] ++ as.slice(takersSize, takersSize + capacity),
                  IQueue
                    .empty[(Iterable[A], Promise[Nothing, Unit])]
                    .enqueue(as.drop(takersSize + capacity) -> p)
                )
            }
        }
      case (p, Surplus(values, putters)) =>
        val size = values.size
        if (as.size + size <= capacity && putters.isEmpty) {
          p.complete(()) -> Surplus(values ++ as, putters)
        } else {
          val (valuesToAdd, surplusValues) = as.splitAt(capacity - size)
          IO.now(false) -> Surplus(values ++ valuesToAdd, putters.enqueue(surplusValues -> p))
        }
    }

    val release: (Boolean, Promise[Nothing, Unit]) => IO[Nothing, Unit] = {
      case (_, p) => removePutter(p)
    }

    Promise.bracket[Nothing, State[A], Unit, Boolean](ref)(acquire)(release)
  }
}

object Queue {

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   */
  final def bounded[A](capacity: Int): IO[Nothing, Queue[A]] =
    Ref[State[A]](Surplus[A](IQueue.empty, IQueue.empty)).map(new Queue[A](capacity, _))

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[A]: IO[Nothing, Queue[A]] = bounded(Int.MaxValue)

  private[ioqueue] object internal {

    sealed trait State[A] {
      def size: Int
    }
    final case class Deficit[A](takers: IQueue[Promise[Nothing, A]]) extends State[A] {
      def size: Int = -takers.length
    }

    final case class Surplus[A](
      queue: IQueue[A],
      putters: IQueue[(Iterable[A], Promise[Nothing, Unit])]
    ) extends State[A] {

      def size: Int = queue.size + putters.foldLeft(0) {
        case (length, (as, _)) => length + as.size
      }
    }
  }
}

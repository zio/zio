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
  final def offer(a: A): IO[Nothing, Unit] = offerAll(List(a))

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  final def takeAll: IO[Nothing, List[A]] =
    ref.modify[List[A]] {
      case Surplus(values, putters) =>
        (values.toList, Surplus(IQueue.empty[A], putters))
      case state @ Deficit(_) => (List.empty[A], state)
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
                (
                  p.complete(a.head),
                  Surplus(IQueue.empty, IQueue.empty.enqueue((a.tail, putter) :: putters.toList))
                )
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
        val (q1, q2) = values.splitAt(max)

        (q1.toList, Surplus(q2, putters))
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
    }.void

  final private def removeTaker(taker: Promise[Nothing, A]): IO[Nothing, Unit] =
    ref.update {
      case Deficit(takers) =>
        Deficit(takers.filterNot(_ == taker))

      case d => d
    }.void

  /**
   * Places the values in the queue. If the queue has reached capacity, then
   * the fiber performing the `offerAll` will be suspended until there is room in
   * the queue.
   */
  final def offerAll(as: Iterable[A]): IO[Nothing, Unit] = {

    val acquire: (Promise[Nothing, Unit], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        takers.dequeueOption match {
          case None =>
            val (addToQueue, surplusValues) = as.splitAt(capacity)
            val (complete, putters) =
              if (surplusValues.isEmpty)
                p.complete(()) -> IQueue.empty
              else
                IO.now(false) -> IQueue.empty.enqueue(surplusValues -> p)

            complete -> Surplus(IQueue.empty.enqueue(addToQueue.toList), putters)

          case Some(_) =>
            val (takersToBeCompleted, deficitValues) = takers.splitAt(as.size)
            val completeTakers = {
              val completedValues = as.take(takersToBeCompleted.size)
              completedValues.zipWithIndex
                .foldLeft[IO[Nothing, Boolean]](IO.now(true)) {
                  case (complete, (a, index)) =>
                    val p = takersToBeCompleted(index)
                    complete *> p.complete(a)
                }
            }
            if (deficitValues.isEmpty) {
              val (addToQueue, surplusValues) =
                as.drop(takers.size).splitAt(capacity)
              val (complete, putters) =
                if (surplusValues.isEmpty)
                  completeTakers *> p.complete(()) -> IQueue.empty
                else IO.now(false)                 -> IQueue.empty.enqueue(surplusValues -> p)

              completeTakers *> complete -> Surplus(
                IQueue.empty[A].enqueue(addToQueue.toList),
                putters
              )
            } else completeTakers *> p.complete(()) -> Deficit(deficitValues)
        }
      case (p, Surplus(values, putters)) =>
        val (addToQueue, surplusValues) = as.splitAt(capacity - values.size)
        val (complete, newPutters) =
          if (surplusValues.isEmpty)
            p.complete(())   -> putters
          else IO.now(false) -> putters.enqueue(surplusValues -> p)

        complete -> Surplus(values.enqueue(addToQueue.toList), newPutters)
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
    Ref[State[A]](Surplus[A](IQueue.empty, IQueue.empty))
      .map(new Queue[A](capacity, _))

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
      putters: IQueue[(Iterable[A], Promise[Nothing, Unit])])
        extends State[A] {

      def size: Int = queue.size + putters.foldLeft(0) {
        case (length, (as, _)) => length + as.size
      }
    }
  }
}

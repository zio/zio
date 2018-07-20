// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz.zio

import scala.collection.immutable.{ Queue => IQueue }

import Queue.internal._

/**
 * A `Queue` is a lightweight, asynchronous queue. This implementation is
 * naive, if functional, and could benefit from significant optimization.
 *
 * TODO:
 *
 * 1. Investigate using a faster option than `Queue`, because `Queue` has
 *    `O(n)` `length` method.
 * 2. Benchmark to see how slow this implementation is and if there are any
 *    easy ways to improve performance.
 */
class Queue[A] private (capacity: Int, ref: Ref[State[A]]) {

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  final def size[E]: IO[E, Int] = ref.read.map(_.size)

  /**
   * Places the value in the queue. If the queue has reached capacity, then
   * the fiber performing the `offer` will be suspended until there is room in
   * the queue.
   */
  final def offer[E](a: A): IO[E, Unit] = {
    val acquire: (Promise[E, Unit], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        takers.dequeueOption match {
          case None => (p.complete(()), Surplus(IQueue.empty[A].enqueue(a), IQueue.empty))
          case Some((taker, takers)) =>
            (taker.complete[Nothing](a) *> p.complete[Nothing](()), Deficit(takers))
        }

      case (p, Surplus(values, putters)) =>
        if (values.length < capacity && putters.isEmpty) {
          (p.complete(()), Surplus(values.enqueue(a), putters))
        } else {
          (IO.now(false), Surplus(values, putters.enqueue((a, p))))
        }
    }

    val release: (Boolean, Promise[E, Unit]) => IO[Nothing, Unit] = {
      case (_, p) => removePutter(p)
    }

    Promise.bracket(ref)(acquire)(release)
  }

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final def take[E]: IO[E, A] = {

    val acquire: (Promise[E, A], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        (IO.now(false), Deficit(takers.enqueue(p)))
      case (p, Surplus(values, putters)) =>
        values.dequeueOption match {
          case None =>
            putters.dequeueOption match {
              case None =>
                (IO.now(false), Deficit(IQueue.empty.enqueue(p)))
              case Some(((a, putter), putters)) =>
                (putter.complete(()) *> p.complete[Nothing](a), Surplus(IQueue.empty, putters))
            }
          case Some((a, values)) =>
            (p.complete[Nothing](a), Surplus(values, putters))
        }
    }

    val release: (Boolean, Promise[E, A]) => IO[Nothing, Unit] = {
      case (_, p) => removeTaker(p)
    }
    Promise.bracket(ref)(acquire)(release)
  }

  /**
   * Interrupts any fibers that are suspended on `take` because the queue is
   * empty. If any fibers are interrupted, returns true, otherwise, returns
   * false.
   */
  final def interruptTake[E](t: Throwable): IO[E, Boolean] =
    IO.flatten(ref.modifyFold[E, IO[E, Boolean]] {
      case Deficit(takers) if takers.nonEmpty =>
        val forked: IO[E, Fiber[E, List[Boolean]]] = IO.forkAll(takers.toList.map(_.interrupt[E](t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(IQueue.empty[Promise[_, A]]))
      case s =>
        (IO.now(false), s)
    })

  /**
   * Interrupts any fibers that are suspended on `offer` because the queue is
   * at capacity. If any fibers are interrupted, returns true, otherwise,
   * returns  false.
   */
  final def interruptOffer[E](t: Throwable): IO[E, Boolean] =
    IO.flatten(ref.modifyFold[E, IO[E, Boolean]] {
      case Surplus(_, putters) if putters.nonEmpty =>
        val forked: IO[E, Fiber[E, List[Boolean]]] = IO.forkAll(putters.toList.map(_._2.interrupt[E](t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(IQueue.empty[Promise[_, A]]))
      case s =>
        (IO.now(false), s)
    })

  private final def removePutter(putter: Promise[_, Unit]): IO[Nothing, Unit] =
    ref
      .modify[Nothing] {
        case Surplus(values, putters) =>
          Surplus(values, putters.filterNot(_._2 == putter))
        case d => d
      }
      .toUnit

  private final def removeTaker(taker: Promise[_, A]): IO[Nothing, Unit] =
    ref
      .modify[Nothing] {
        case Deficit(takers) =>
          Deficit(takers.filterNot(_ == taker))

        case d => d
      }
      .toUnit

}
object Queue {

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   */
  final def bounded[E, A](capacity: Int): IO[E, Queue[A]] =
    Ref[E, State[A]](Surplus[A](IQueue.empty, IQueue.empty)).map(new Queue[A](capacity, _))

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[E, A]: IO[E, Queue[A]] = bounded(Int.MaxValue)

  private[zio] object internal {
    sealed trait State[A] {
      def size: Int
    }
    final case class Deficit[A](takers: IQueue[Promise[_, A]]) extends State[A] {
      def size: Int = -takers.length
    }
    final case class Surplus[A](queue: IQueue[A], putters: IQueue[(A, Promise[_, Unit])]) extends State[A] {
      def size: Int = queue.size + putters.length // TODO: O(n) for putters.length
    }
  }
}

// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz.zio

import scala.collection.immutable.Queue

import IOQueue.internal._

/**
 * An `IOQueue` is a lightweight, asynchronous queue. This implementation is
 * naive, if functional, and could benefit from significant optimization.
 *
 * TODO:
 *
 * 1. Investigate using a faster option than `Queue`, because `Queue` has
 *    `O(n)` `length` method.
 * 2. We are using `Promise.unsafeMake`. Why? What would the safe way of doing
 *    this be?
 * 3. Benchmark to see how slow this implementation is and if there are any
 *    easy ways to improve performance.
 */
class IOQueue[A] private (capacity: Int, ref: IORef[State[A]]) {

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
  final def offer[E](a: A): IO[E, Unit] =
    for {
      pRef <- IORef[E, Option[Promise[E, Unit]]](None)
      a <- (for {
            p <- ref
                  .modifyFold[Void, (Promise[E, Unit], IO[Void, Boolean])] {
                    case Deficit(takers) =>
                      val p = Promise.unsafeMake[E, Unit]

                      takers.dequeueOption match {
                        case None => ((p, p.complete(())), Surplus(Queue.empty[A].enqueue(a), Queue.empty))
                        case Some((taker, takers)) =>
                          ((p, taker.complete[Void](a) *> p.complete[Void](())), Deficit(takers))
                      }
                    case Surplus(values, putters) =>
                      val p = Promise.unsafeMake[E, Unit]

                      if (values.length < capacity && putters.isEmpty) {
                        ((p, p.complete(())), Surplus(values.enqueue(a), putters))
                      } else {
                        ((p, IO.now(false)), Surplus(values, putters.enqueue((a, p))))
                      }
                  }
                  .flatMap(t => t._2 *> pRef.modifyFold[Void, Unit](_ => ((), Some(t._1))) *> IO.now(t._1))
                  .uninterruptibly
                  .widenError[E]

            _ <- p.get
          } yield ()).ensuring(pRef.read[Void].flatMap(_.fold(IO.unit[Void])(removePutter)))
    } yield a

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final def take[E]: IO[E, A] =
    for {
      pRef <- IORef[E, Option[Promise[E, A]]](None)
      a <- (for {
            p <- ref
                  .modifyFold[Void, (Promise[E, A], IO[Void, Unit])] {
                    case Deficit(takers) =>
                      val p = Promise.unsafeMake[E, A]
                      ((p, IO.unit), Deficit(takers.enqueue(p)))
                    case Surplus(values, putters) =>
                      values.dequeueOption match {
                        case None =>
                          putters.dequeueOption match {
                            case None =>
                              val p = Promise.unsafeMake[E, A]
                              ((p, IO.unit), Deficit(Queue.empty.enqueue(p)))
                            case Some(((a, putter), putters)) =>
                              val p = Promise.unsafeMake[E, A]
                              ((p, putter.complete(()) *> p.complete[Void](a).toUnit), Surplus(Queue.empty, putters))
                          }
                        case Some((a, values)) =>
                          val p = Promise.unsafeMake[E, A]
                          ((p, p.complete[Void](a).toUnit), Surplus(values, putters))
                      }
                  }
                  .flatMap(t => t._2 *> pRef.modifyFold[Void, Unit](_ => ((), Some(t._1))) *> IO.now(t._1))
                  .uninterruptibly
                  .widenError[E]

            a <- p.get

          } yield a).ensuring(pRef.read[Void].flatMap(_.fold(IO.unit[Void])(removeTaker)))
    } yield a

  /**
   * Interrupts any fibers that are suspended on `take` because the queue is
   * empty. If any fibers are interrupted, returns true, otherwise, returns
   * false.
   */
  final def interruptTake[E](t: Throwable): IO[E, Boolean] =
    IO.flatten(ref.modifyFold[E, IO[E, Boolean]] {
      case Deficit(takers) if takers.nonEmpty =>
        val forked: IO[E, Fiber[E, List[Boolean]]] = IO.forkAll(takers.toList.map(_.interrupt[E](t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(Queue.empty[Promise[_, A]]))
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
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(Queue.empty[Promise[_, A]]))
      case s =>
        (IO.now(false), s)
    })

  private final def removePutter(putter: Promise[_, Unit]): IO[Void, Unit] =
    ref
      .modify[Void] {
        case Surplus(values, putters) =>
          Surplus(values, putters.filterNot(_._2 == putter))
        case d => d
      }
      .toUnit

  private final def removeTaker(taker: Promise[_, A]): IO[Void, Unit] =
    ref
      .modify[Void] {
        case Deficit(takers) =>
          Deficit(takers.filterNot(_ == taker))

        case d => d
      }
      .toUnit

}
object IOQueue {

  /**
   * Makes a new queue.
   */
  final def make[E, A](capacity: Int): IO[E, IOQueue[A]] =
    IORef[E, State[A]](Surplus[A](Queue.empty, Queue.empty)).map(new IOQueue[A](capacity, _))

  private[zio] object internal {
    sealed trait State[A] {
      def size: Int
    }
    final case class Deficit[A](takers: Queue[Promise[_, A]]) extends State[A] {
      def size: Int = -takers.length
    }
    final case class Surplus[A](queue: Queue[A], putters: Queue[(A, Promise[_, Unit])]) extends State[A] {
      def size: Int = queue.size + putters.length // TODO: O(n) for putters.length
    }
  }
}

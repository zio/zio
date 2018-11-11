// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz.zio

import scala.collection.immutable.{ Queue => IQueue }
import Queue.internal._

/**
 *  A `Queue[A]` is a lightweight, asynchronous queue for values of type `A`.
 */
class Queue[A] private (
  capacity: Option[Int],
  ref: Ref[State[A]],
  strategy: SurplusStrategy
) extends Serializable {

  /**
   * Places one value in the queue.
   */
  final def offer(a: A): IO[Nothing, Boolean] = offerAll(List(a))

  /**
   * For Bounded Queue: uses the `BackPressure` Strategy, places the values in the queue and returns always true
   * If the queue has reached capacity, then
   * the fiber performing the `offerAll` will be suspended until there is room in
   * the queue.
   *
   * For Unbounded Queue:
   * Places all values in the queue and returns true.
   *
   * For Sliding Queue: uses `Sliding` Strategy
   * If there is a room in the queue, it places the values and returns true otherwise it removed the old elements and
   * enqueues the new ones
   *
   * For Dropping Queue: uses `Dropping` Strategy,
   * It places the values in the queue but if there is no room it will not enqueue them and returns false
   *
   */
  final def offerAll(as: Iterable[A]): IO[Nothing, Boolean] = {

    val acquire: (Promise[Nothing, Boolean], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers, hook)) =>
        takers.dequeueOption match {
          case None =>
            val (addToQueue, surplusValues) = capacity.fold((as, Iterable.empty[A]))(as.splitAt)
            if (surplusValues.isEmpty)
              p.complete(true) -> Surplus(IQueue.empty.enqueue(addToQueue.toList), IQueue.empty, hook)
            else
              strategy match {
                case BackPressure =>
                  IO.now(true) -> Surplus(
                    IQueue.empty.enqueue(addToQueue.toList),
                    IQueue.empty.enqueue(surplusValues -> p),
                    hook
                  )
                case Sliding =>
                  val toQueue = capacity.fold(as)(as.takeRight)
                  p.complete(false) -> Surplus(
                    IQueue.empty.enqueue(toQueue.toList),
                    IQueue.empty,
                    hook
                  )
                case Dropping =>
                  p.complete(false) -> Surplus(
                    IQueue.empty.enqueue(addToQueue.toList),
                    IQueue.empty,
                    hook
                  )
              }

          case Some(_) =>
            val (takersToBeCompleted, deficitValues) = takers.splitAt(as.size)
            val completeTakers =
              as.take(takersToBeCompleted.size)
                .zipWithIndex
                .foldLeft[IO[Nothing, Boolean]](IO.now(true)) {
                  case (complete, (a, index)) =>
                    val p = takersToBeCompleted(index)
                    complete *> p.complete(a)
                }

            if (deficitValues.isEmpty) {
              val (addToQueue, surplusValues) =
                capacity.fold((as, Iterable.empty[A]))(as.drop(takers.size).splitAt)

              val (complete, surplus) =
                if (surplusValues.isEmpty)
                  p.complete(true) -> Surplus(
                    IQueue.empty.enqueue(addToQueue.toList),
                    IQueue.empty,
                    hook
                  )
                else
                  strategy match {
                    case BackPressure =>
                      IO.now(true) -> Surplus(
                        IQueue.empty[A].enqueue(addToQueue.toList),
                        IQueue.empty.enqueue(surplusValues -> p),
                        hook
                      )
                    case Sliding =>
                      val notTaken = addToQueue ++ surplusValues
                      val toQueue  = capacity.fold(notTaken)(notTaken.takeRight)
                      p.complete(false) -> Surplus(
                        IQueue.empty.enqueue(toQueue.toList),
                        IQueue.empty,
                        hook
                      )
                    case Dropping =>
                      p.complete(false) -> Surplus(
                        IQueue.empty.enqueue(addToQueue.toList),
                        IQueue.empty,
                        hook
                      )
                  }

              completeTakers *> complete -> surplus
            } else
              completeTakers *> p.complete(true) -> Deficit(deficitValues, hook)
        }

      case (p, Surplus(values, putters, hook)) =>
        val (addToQueue, surplusValues) =
          capacity.fold((as, Iterable.empty[A]))(c => as.splitAt(c - values.size))
        if (surplusValues.isEmpty)
          (p.complete(true), Surplus(values.enqueue(addToQueue.toList), putters, hook))
        else {
          strategy match {
            case BackPressure =>
              (
                IO.now(true),
                Surplus(values.enqueue(addToQueue.toList), putters.enqueue(surplusValues -> p), hook)
              )
            case Sliding =>
              (
                p.complete(true),
                capacity.fold(
                  Surplus(values.enqueue(as.toList), putters, hook)
                ) { c =>
                  Surplus(
                    values
                      .takeRight(c - as.size)
                      .enqueue(as.takeRight(c).toList),
                    putters,
                    hook
                  )
                }
              )
            case Dropping =>
              (
                p.complete(false),
                Surplus(values.enqueue(addToQueue.toList), putters, hook)
              )
          }
        }

      case (p, state @ Shutdown) => (p.interrupt, state)
    }

    val release: (Boolean, Promise[Nothing, Boolean]) => IO[Nothing, Unit] = {
      case (_, p) => p.poll.void <> removePutter(p)
    }

    Promise.bracket[Nothing, State[A], Boolean, Boolean](ref)(acquire)(release)
  }

  /**
   * Waits until the queue is shutdown.
   * The `IO` returned by this method will not resume until the queue has been shutdown.
   * If the queue is already shutdown, the `IO` will resume right away.
   */
  final def awaitShutdown: IO[Nothing, Unit] =
    Promise
      .make[Nothing, Unit]
      .flatMap(promise => {
        val io = promise.complete(())
        IO.flatten(ref.modify {
          case Deficit(takers, hook)         => IO.unit -> Deficit(takers, hook *> io.void)
          case Surplus(queue, putters, hook) => IO.unit -> Surplus(queue, putters, hook *> io.void)
          case state @ Shutdown              => io.void -> state
        }) *> promise.get
      })

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  final val size: IO[Nothing, Int] = ref.get.flatMap(_.size)

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  final val shutdown: IO[Nothing, Unit] =
    IO.flatten(ref.modify {
      case Surplus(_, putters, hook) =>
        if (putters.nonEmpty) {
          val forked = IO
            .forkAll[Nothing, Boolean](putters.toList.map {
              case (_, p) => p.interrupt
            })
            .flatMap(_.join)
          (forked *> hook, Shutdown)
        } else (hook, Shutdown)
      case Deficit(takers, hook) =>
        if (takers.nonEmpty) {
          val forked = IO
            .forkAll[Nothing, Boolean](
              takers.toList.map(p => p.interrupt)
            )
            .flatMap(_.join)
          (forked *> hook, Shutdown)
        } else (hook, Shutdown)
      case Shutdown => (IO.unit, Shutdown)
    })

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final val take: IO[Nothing, A] = {

    val acquire: (Promise[Nothing, A], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers, hook)) => (IO.now(false), Deficit(takers.enqueue(p), hook))
      case (p, Surplus(values, putters, hook)) =>
        strategy match {
          case Sliding | Dropping if capacity.exists(_ < 1) =>
            (IO.never, Surplus(IQueue.empty, putters, hook))
          case _ =>
            values.dequeueOption match {
              case None if putters.isEmpty =>
                (IO.now(false), Deficit(IQueue.empty.enqueue(p), hook))
              case None =>
                val (newSurplus, promise) =
                  moveNPutters(Surplus(values, putters, hook), 1)
                newSurplus.queue.dequeueOption match {
                  case None => (promise *> IO.now(false), newSurplus)
                  case Some((a, values)) =>
                    (
                      promise *> p.complete(a),
                      Surplus(values, newSurplus.putters, hook)
                    )
                }
              case Some((a, values)) =>
                val (newSurplus, promise) =
                  moveNPutters(Surplus(values, putters, hook), 1)
                (promise *> p.complete(a), newSurplus)

            }
        }
      case (p, state @ Shutdown) => (p.interrupt, state)
    }

    val release: (Boolean, Promise[Nothing, A]) => IO[Nothing, Unit] = {
      case (_, p) => p.poll.void <> removeTaker(p)
    }

    Promise.bracket[Nothing, State[A], A, Boolean](ref)(acquire)(release)

  }

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  final val takeAll: IO[Nothing, List[A]] =
    IO.flatten(ref.modify[IO[Nothing, List[A]]] {
      case Surplus(values, putters, hook) =>
        val (newState, promises) = moveNPutters(Surplus(IQueue.empty, putters, hook), values.size)
        (promises *> IO.point(values.toList), newState)
      case state @ Deficit(_, _) => (IO.point(List.empty[A]), state)
      case state @ Shutdown      => (IO.interrupt, state)
    })

  /**
   * Takes up to max number of values in the queue.
   */
  final def takeUpTo(max: Int): IO[Nothing, List[A]] =
    IO.flatten(ref.modify[IO[Nothing, List[A]]] {
      case Surplus(values, putters, hook) =>
        val (q1, q2)             = values.splitAt(max)
        val (newState, promises) = moveNPutters(Surplus(q2, putters, hook), q1.size)
        (promises *> IO.point(q1.toList), newState)
      case state @ Deficit(_, _) => (IO.now(Nil), state)
      case state @ Shutdown      => (IO.interrupt, state)
    })

  private final def moveNPutters(surplus: Surplus[A], n: Int): (Surplus[A], IO[Nothing, Unit]) = {
    val (newSurplus, _, completedPutters) =
      surplus.putters.foldLeft((Surplus(surplus.queue, IQueue.empty, surplus.shutdownHook), n, IO.unit)) {
        case ((surplus, 0, io), p) =>
          (Surplus(surplus.queue, surplus.putters.enqueue(p), surplus.shutdownHook), 0, io)
        case ((surplus, cpt, io), (values, promise)) =>
          val (add, rest) = values.splitAt(cpt)
          if (rest.isEmpty)
            (
              Surplus(
                surplus.queue.enqueue(add.toList),
                surplus.putters,
                surplus.shutdownHook
              ),
              cpt - add.size, // if we have more elements in putters we can complete the next elements until cpt = 0
              io *> promise.complete(true).void
            )
          else
            (
              Surplus(
                surplus.queue.enqueue(add.toList),
                surplus.putters.enqueue((rest, promise)),
                surplus.shutdownHook
              ),
              0,
              io
            )
      }
    (newSurplus, completedPutters)
  }

  private final def removePutter(putter: Promise[Nothing, Boolean]): IO[Nothing, Unit] =
    ref.update {
      case Surplus(values, putters, hook) =>
        Surplus(values, putters.filterNot { case (_, p) => p == putter }, hook)
      case d => d
    }.void

  private final def removeTaker(taker: Promise[Nothing, A]): IO[Nothing, Unit] =
    ref.update {
      case Deficit(takers, hook) =>
        Deficit(takers.filterNot(_ == taker), hook)
      case d => d
    }.void

}

object Queue {

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   */
  final def bounded[A](capacity: Int): IO[Nothing, Queue[A]] =
    createQueue(Some(capacity), BackPressure)

  /**
   * Makes a new bounded queue with sliding strategy.
   * When the capacity of the queue is reached, new elements will be added and the old elements
   * will be dropped.
   */
  final def sliding[A](capacity: Int): IO[Nothing, Queue[A]] = createQueue(Some(capacity), Sliding)

  /**
   * Makes a new bounded queue with the dropping strategy.
   * When the capacity of the queue is reached, new elements will be dropped.
   */
  final def dropping[A](capacity: Int): IO[Nothing, Queue[A]] =
    createQueue(Some(capacity), Dropping)

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[A]: IO[Nothing, Queue[A]] = createQueue(None, BackPressure)

  private final def createQueue[A](
    capacity: Option[Int],
    strategy: SurplusStrategy
  ): IO[Nothing, Queue[A]] =
    Ref[State[A]](Surplus[A](IQueue.empty, IQueue.empty, IO.unit)).map(state => new Queue[A](capacity, state, strategy))

  private[zio] object internal {

    sealed trait SurplusStrategy

    case object Sliding extends SurplusStrategy

    case object Dropping extends SurplusStrategy

    case object BackPressure extends SurplusStrategy

    sealed trait State[+A] {
      def size: IO[Nothing, Int]
    }

    final case class Deficit[A](takers: IQueue[Promise[Nothing, A]], shutdownHook: IO[Nothing, Unit]) extends State[A] {
      def size: IO[Nothing, Int] = IO.point(-takers.length)
    }

    final case object Shutdown extends State[Nothing] {
      def size: IO[Nothing, Int] = IO.interrupt
    }

    final case class Surplus[A](
      queue: IQueue[A],
      putters: IQueue[(Iterable[A], Promise[Nothing, Boolean])],
      shutdownHook: IO[Nothing, Unit]
    ) extends State[A] {

      def size: IO[Nothing, Int] = IO.point {
        queue.size + putters.foldLeft(0) {
          case (length, (as, _)) => length + as.size
        }
      }
    }

  }

}

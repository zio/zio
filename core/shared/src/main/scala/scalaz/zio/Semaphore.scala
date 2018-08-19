// Copyright (C) 2017-2018 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package scalaz.zio

import internals._
import scalaz.zio.ExitResult.Terminated

import scala.annotation.tailrec
import scala.collection.immutable.{ Queue => IQueue }

final class Semaphore private (private val state: Ref[State]) {

  def count: IO[Nothing, Long] = state.get.map(count_)

  def available: IO[Nothing, Long] = state.get.map {
    case Left(_)  => 0
    case Right(n) => n
  }

  def acquire: IO[Nothing, Unit] = acquireN(1)

  def release: IO[Nothing, Unit] = releaseN(1)

  def withPermit[E, A](task: IO[E, A]): IO[E, A] =
    IO.bracket[E, Unit, A](acquire)(_ => release)(_ => task)

  def acquireN(requested: Long): IO[Nothing, Unit] =
    assertNonNegative(requested) *>
      mkGate.flatMap { gate =>
        state.update {
          case Left((head, queue)) =>
            Left(head -> queue.enqueue(requested -> gate))
          case Right(available) =>
            if (requested <= available) Right(available - requested)
            else Left(((requested - available) -> gate, IQueue.empty[Entry]))

        }.flatMap {
          case Left((head, _)) => awaitGate(head)
          case Right(_)        => IO.unit
        }
      }

  def releaseN(toRelease: Long): IO[Nothing, Unit] =
    assertNonNegative(toRelease) *>
      state.modify { old =>
        @tailrec def releaseRecursively(waiting: NonEmptyQueue, available: Long): State =
          waiting match {
            // n + 1 in queue case
            case ((requested, gate), tail) if tail.nonEmpty && available > 0 =>
              if (requested > available) releaseRecursively(((requested - available, gate), tail), available = 0)
              else releaseRecursively(tail.dequeue, available - requested)
            // 1 in queue case
            case ((requested, gate), tail) if tail.isEmpty && available > 0 =>
              if (requested > available) Left(((requested - available, gate), tail))
              else Right(available - requested)
            // n in queue but no more to release
            case nonEmptyQueue if available == 0 => Left(nonEmptyQueue)
          }

        val updated: State = old match {
          case Left(waiting) =>
            releaseRecursively(waiting, toRelease)
          case Right(available) =>
            Right(available + toRelease)
        }

        ((old, updated), updated)
      }.flatMap {
        case (previous, now) =>
          previous match {
            case Left(neq) =>
              val newSize = now match {
                case Left(newNeq) => newNeq.size
                case Right(_)     => 0
              }
              val released = neq.size - newSize
              neq.take(released).foldRight(IO.unit) { (entry, unit) =>
                openGate(entry) *> unit
              }
            case Right(_) => IO.unit
          }
      }

  private def mkGate: IO[Nothing, Promise[Nothing, Unit]] = Promise.make[Nothing, Unit]

  private def awaitGate(entry: (Long, Promise[Nothing, Unit])): IO[Nothing, Unit] =
    IO.unit.bracket0[Nothing, Unit] { (_, useOutcome) =>
      useOutcome match {
        case _: Terminated[Nothing, Unit] => // Terminated outcome means either interruption or uncaught exception
          state.update {
            case Left((current, rest)) =>
              // if entry is NonEmpty's head and queue is empty, swap to Right, but without any permits
              if (current == entry && rest.isEmpty) Right(0)
              // if entry is NonEmpty's head and queue is not empty, just drop this entry from head position
              else if (current == entry && rest.nonEmpty) Left(rest.dequeue)
              // this entry is not current NonEmpty's head, just drop it from queue
              else Left((current, rest.filter(_ != entry)))

            case Right(m) => Right(m)
          }.void
        case _ =>
          IO.unit
      }
    }(_ => entry._2.get)

  private def openGate[E](entry: (Long, Promise[E, Unit])): IO[E, Unit] =
    entry._2.complete(()).void

  private def count_(state: State): Long = state match {
    case Left((head, tail)) => -(head._1 + tail.map(_._1).sum)
    case Right(available)   => available
  }

}

object Semaphore {
  def apply(permits: Long): IO[Nothing, Semaphore] = Ref[State](Right(permits)).map(new Semaphore(_))
}

private object internals {

  type NonEmpty[F[_], A] = (A, F[A])

  type Entry = (Long, Promise[Nothing, Unit])

  type NonEmptyQueue = NonEmpty[IQueue, Entry]

  type State = Either[NonEmptyQueue, Long]

  def assertNonNegative(n: Long): IO[Nothing, Unit] =
    if (n < 0) IO.terminate(new NegativeArgument(s"Unexpected negative value `$n` passed to acquireN or releaseN."))
    else IO.unit

  class NegativeArgument(message: String) extends IllegalArgumentException(message)

  implicit class NonEmptyQueueOps(val nonEmptyQueue: NonEmptyQueue) extends AnyVal {
    def size: Int                   = nonEmptyQueue._2.size + 1
    def take(n: Int): NonEmptyQueue = (nonEmptyQueue._1, nonEmptyQueue._2.take(n - 1))
    def foldRight[B](zero: => B)(f: (Entry, B) => B): B = {
      @tailrec def foldR(acc: B, neq: NonEmptyQueue): B = neq match {
        case (head, tail) if tail.nonEmpty => foldR(f(head, acc), tail.dequeue)
        case (head, tail) if tail.isEmpty  => f(head, acc)
      }

      foldR(zero, nonEmptyQueue)
    }
  }

}

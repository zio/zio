package scalaz.zio

import scala.collection.immutable.{ Queue => IQueue }
import scala.util.control.NoStackTrace

abstract class Semaphore {

  import Semaphore.NegativeArgument

  //  def available[E]: IO[E, Long]

  //  def count[E]: IO[E, Long]

  def acquireN(n: Long): IO[NegativeArgument.type, Unit]

  def acquire: IO[Void, Unit] = acquireN(1).attempt[Void].toUnit

  //  def tryAcquireN[E](n: Long): IO[E, Boolean]

  //  def tryAcquire[E]: IO[E, Boolean] = tryAcquireN(1)

  def releaseN(n: Long): IO[NegativeArgument.type, Unit]

  def release: IO[Void, Unit] = releaseN(1).attempt[Void].toUnit

  //  def withPermit[E, A](t: IO[E, A]): IO[E, A]

}

object Semaphore {

  private def assertNonNegative(n: Long): IO[NegativeArgument.type, Unit] =
    if (n < 0) IO.fail(NegativeArgument) else IO.unit

  // making this an object with no stack trace might not be a good idea for debugging
  object NegativeArgument extends IllegalArgumentException with NoStackTrace

  private type State = Either[IQueue[(Long, Promise[NegativeArgument.type, Unit])], Long]

  private class ConcreteSemaphore(state: Ref[State]) extends Semaphore {

    private def mkGate[E]: IO[E, Promise[E, Unit]] = Promise.make[E, Unit]

    private def awaitGate[E](entry: (Long, Promise[E, Unit])): IO[E, Unit] =
      IO.unit[E]
        .bracket0[Unit] { (_, useOutcome) =>
          useOutcome match {
            case None => // None outcome means either cancellation or uncaught exception
              state.modify {
                case Left(waiting) => Left(waiting.filter(_ != entry))
                case Right(m)      => Right(m)
              }.toUnit
            case _ =>
              IO.unit
          }
        }(_ => entry._2.get)

    private def openGate[E](entry: (Long, Promise[E, Unit])): IO[E, Unit] =
      entry._2.complete(()).toUnit

    override def acquireN(requested: Long): IO[NegativeArgument.type, Unit] =
      assertNonNegative(requested) *>
        mkGate[NegativeArgument.type].flatMap { gate =>
          state.modify {
            case Left(waiting) => Left(waiting :+ (requested -> gate))
            case Right(available) =>
              if (requested <= available) Right(available - requested)
              else Left(IQueue((requested - available) -> gate))
          }.flatMap {
            case Left(waiting) =>
              val entry = waiting.lastOption
                .getOrElse(sys.error("Semaphore has empty waiting queue rather than 0 count")) // todo is this even legal?

              awaitGate[NegativeArgument.type](entry)

            case Right(_) => IO.unit[NegativeArgument.type]
          }
        }

    override def releaseN(toRelease: Long): IO[NegativeArgument.type, Unit] =
      assertNonNegative(toRelease) *>
        state.modifyFold { old =>
          val updated: State = old match {
            case Left(waiting) =>
              var available = toRelease
              var waiting2  = waiting
              while (waiting2.nonEmpty && available > 0) {
                val (requested, gate) = waiting2.head
                if (requested > available) {
                  waiting2 = (requested - available, gate) +: waiting2.tail
                  available = 0
                } else {
                  available -= requested
                  waiting2 = waiting2.tail
                }
              }
              if (waiting2.nonEmpty) Left(waiting2)
              else Right(available)
            case Right(available) =>
              Right(available + toRelease)
          }

          ((old, updated), updated)
        }.flatMap {
          case (previous, now) =>
            previous match {
              case Left(waiting) =>
                val newSize = now match {
                  case Left(w)  => w.size
                  case Right(_) => 0
                }
                val released = waiting.size - newSize
                waiting.take(released).foldRight(IO.unit[NegativeArgument.type]) { (entry, unit) =>
                  openGate(entry) *> unit
                }
              case Right(_) => IO.unit
            }
        }

  }

  def apply[E](permits: Long): IO[E, Semaphore] = Ref[E, State](Right(permits)).map(new ConcreteSemaphore(_))
}

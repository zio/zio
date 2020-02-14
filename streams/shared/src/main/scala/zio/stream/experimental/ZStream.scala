package zio.stream.experimental

import zio._

class ZStream[-R, +E, -M, +B, +A](
  val process: ZManaged[R, Nothing, ZStream.Control[R, E, M, B, A]]
) extends AnyVal
    with Serializable { self =>
  import ZStream.Command
  import ZStream.Control
  import ZStream.Pull

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` elements in a queue.
   *
   * @param capacity The size of the buffer
   * @note Prefer capacities that are powers of 2 for better performance.
   * @return a stream where the producer and consumer can progress concurrently
   */
  final def buffer(capacity: Int): ZStream[R, E, M, B, A] =
    ZStream {
      for {
        done  <- Ref.make[Option[B]](None).toManaged_
        queue <- self.toQueue(capacity)
        pull = done.get.flatMap {
          case Some(b) => Pull.endNow(b)
          case None =>
            queue.take.flatMap {
              case Take.Fail(c)  => Pull.haltNow(c)
              case Take.Value(a) => Pull.emitNow(a)
              case Take.End(b)   => done.set(Some(b)) *> Pull.endNow(b)
            }
        }
      } yield Control(pull, Command.noop)
    }

  /**
   * Maps the elements of this stream using a ''pure'' function.
   *
   * @tparam C the value type of the new stream
   * @param f the ''pure'' transformation function
   * @return a stream of transformed values
   */
  def map[C](f: A => C): ZStream[R, E, M, B, C] =
    ZStream {
      self.process.map { control =>
        Control(
          control.pull.map(f),
          control.command
        )
      }
    }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   * @param pred predicate used for deciding whether element should be retained
   * @return a stream containing only elements from the original stream that satisfy given `pred`
   */
  def filter(pred: A => Boolean): ZStream[R, E, M, B, A] =
    ZStream {
      self.process.map { control =>
        Control(
          control.pull.flatMap { a =>
            if (pred(a)) UIO.succeedNow(a)
            else control.pull
          },
          control.command
        )
      }
    }

  /**
   * Enqueues elements of this stream into a queue. Stream failure and ending will also be
   * signalled.
   *
   * @tparam R1 the requirement for this action to run
   * @tparam E1 the checked errors that may happen
   * @tparam B1 the marker for the end of the stream
   * @tparam A1 the values pulled from the stream
   * @param queue a queue of values representing the result of pulling from the stream
   * @return an action that drains the stream into a queue
   */
  final def into[R1 <: R, E1 >: E, B1 >: B, A1 >: A](
    queue: ZQueue[R1, Nothing, Nothing, Any, Take[E1, B1, A1], Any]
  ): ZIO[R1, E1, Unit] =
    intoManaged(queue).use_(UIO.unit)

  /**
   * Like [[ZStream#into]], but provides the result as a [[ZManaged]] to allow for scope
   * composition.
   *
   * @tparam R1 the requirement for this action to run
   * @tparam E1 the checked errors that may happen
   * @tparam B1 the marker for the end of the stream
   * @tparam A1 the values pulled from the stream
   * @param queue a queue of values representing the result of pulling from the stream
   * @return an managed resource where the acquisition will drain the stream into a queue
   */
  final def intoManaged[R1 <: R, E1 >: E, B1 >: B, A1 >: A](
    queue: ZQueue[R1, Nothing, Nothing, Any, Take[E1, B1, A1], Any]
  ): ZManaged[R1, E1, Unit] =
    for {
      control <- self.process
      pull = {
        def go: ZIO[R1, Nothing, Unit] =
          Take.fromPull(control.pull).tap(queue.offer(_)).flatMap {
            case Take.End(_) => UIO.unit
            case _           => go
          }

        go
      }
      _ <- pull.toManaged_
    } yield ()

  /**
   * Converts the stream to a managed queue. After the managed queue is used,
   * the queue will never again produce values and should be discarded.
   *
   * @tparam E1 the checked errors that may happen
   * @tparam B1 the marker for the end of the stream
   * @tparam A1 the values pulled from the stream
   * @param queue a queue of values representing the result of pulling from the stream
   * @return a managed resources that yields the queue
   */
  final def toQueue[E1 >: E, B1 >: B, A1 >: A](capacity: Int = 2): ZManaged[R, Nothing, Queue[Take[E1, B1, A1]]] =
    for {
      queue <- Queue.bounded[Take[E1, B1, A1]](capacity).toManaged(_.shutdown)
      _     <- self.intoManaged(queue).fork
    } yield queue

}

object ZStream extends Serializable {

  final case class Control[-R, +E, -M, +B, +A](
    pull: ZIO[R, Either[E, B], A],
    command: M => ZIO[R, E, Any]
  )

  object Pull extends Serializable {
    def end[B](b: => B): IO[Either[Nothing, B], Nothing]         = IO.fail(Right(b))
    def emit[A](a: => A): UIO[A]                                 = UIO.succeed(a)
    def fail[E](e: => E): IO[Either[E, Nothing], Nothing]        = IO.fail(Left(e))
    def halt[E](c: => Cause[E]): IO[Either[E, Nothing], Nothing] = IO.halt(c.map(Left(_)))

    def endNow[B](b: B): IO[Either[Nothing, B], Nothing]         = IO.failNow(Right(b))
    def emitNow[A](a: A): UIO[A]                                 = UIO.succeedNow(a)
    def failNow[E](e: E): IO[Either[E, Nothing], Nothing]        = IO.failNow(Left(e))
    def haltNow[E](c: Cause[E]): IO[Either[E, Nothing], Nothing] = IO.haltNow(c.map(Left(_)))

    val endUnit: IO[Either[Nothing, Unit], Nothing] = endNow(())

    def fromTakeNow[E, B, A](take: Take[E, B, A]): IO[Either[E, B], A] =
      take match {
        case Take.Value(a) => emitNow(a)
        case Take.Fail(e)  => haltNow(e)
        case Take.End(b)   => endNow(b)
      }
  }

  object Command extends Serializable {
    val noop: Any => UIO[Unit] = _ => UIO.unit
  }

  /**
   * Creates a stream from a scoped [[Control]].
   *
   * @tparam R the stream environment type
   * @tparam E the stream error type
   * @tparam M the stream input message type
   * @tparam B the stream exit value type
   * @tparam A the stream value type
   * @param process the scoped control
   * @return a new stream wrapping the scoped control
   */
  def apply[R, E, M, B, A](process: ZManaged[R, Nothing, Control[R, E, M, B, A]]): ZStream[R, E, M, B, A] =
    new ZStream(process)

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   *
   * @tparam A the value type
   * @param c a chunk of values
   * @return a finite stream of values
   */
  def fromChunk[A](c: => Chunk[A]): ZStream[Any, Nothing, Any, Unit, A] =
    ZStream {
      Managed.fromEffect {
        Ref.make(0).map { iRef =>
          val l = c.length
          val pull = iRef.modify { i =>
            if (i >= l)
              Pull.endUnit -> i
            else
              Pull.emit(c(i)) -> (i + 1)
          }.flatten

          Control(pull, Command.noop)
        }
      }
    }

  /**
   * Creates a single-valued stream from an effect.
   *
   * @tparam R the environment type
   * @tparam E the error type
   * @tparam A the effect value type
   * @param effect the effect
   * @return a single-valued stream
   */
  def fromEffect[R, E, A](effect: ZIO[R, E, A]): ZStream[R, E, Any, Unit, A] =
    ZStream {
      for {
        done <- Ref.make(false).toManaged_
        pull = done.get.flatMap {
          if (_) Pull.endUnit
          else
            (for {
              _ <- done.set(true)
              a <- effect
            } yield a).mapError(Left(_))
        }
      } yield Control(pull, Command.noop)
    }

  /**
   * Creates a single-valued stream from a managed resource.
   *
   * @tparam R the environment type
   * @tparam E the error type
   * @tparam A the managed resource type
   * @param managed the managed resource
   * @return a single-valued stream
   */
  def managed[R, E, A](managed: ZManaged[R, E, A]): ZStream[R, E, Any, Unit, A] =
    ZStream {
      for {
        doneRef   <- Ref.make(false).toManaged_
        finalizer <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull = ZIO.uninterruptibleMask { restore =>
          doneRef.get.flatMap { done =>
            if (done) Pull.endUnit
            else
              (for {
                _           <- doneRef.set(true)
                reservation <- managed.reserve
                _           <- finalizer.set(reservation.release)
                a           <- restore(reservation.acquire)
              } yield a).mapError(Left(_))
          }
        }
      } yield Control(pull, Command.noop)
    }
}

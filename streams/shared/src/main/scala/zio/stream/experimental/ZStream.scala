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
   * Executes the provided finalizer after this stream's finalizers run.
   *
   * @tparam R1 the environment required by the finalizer
   * @param fin the finalizer
   * @return a stream that executes the provided finalizer in addition to the existing ones
   */
  final def ensuring[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, M, B, A] =
    ZStream(self.process.ensuring(fin))

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   *
   * @tparam R1 the environment required by the finalizer
   * @param fin the finalizer
   * @return a stream that executes the provided finalizer in addition to the existing ones
   */
  final def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, M, B, A] =
    ZStream(self.process.ensuringFirst(fin))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   *
   * @tparam R1 the type of requirement
   * @tparam E1 the type of errors
   * @tparam M1 the type of commands
   * @tparam B1 the type of marker
   * @tparam C the type of values
   * @param f0 a function that yields a new stream for each value produced by this stream
   * @return a stream made of the concatenation in strict order of all the streams
   */
  final def flatMap[R1 <: R, E1 >: E, M1 <: M, B1 >: B, C](
    f0: A => ZStream[R1, E1, M1, B1, C]
  ): ZStream[R1, E1, M1, B1, C] = {
    def go(
      as: ZIO[R1, Either[E1, B1], A],
      finalizer: Ref[Exit[_, _] => URIO[R1, _]],
      currPull: Ref[Option[ZIO[R1, Either[E1, B1], C]]],
      currCmd: Ref[Option[M1 => ZIO[R1, E1, Any]]]
    ): ZIO[R1, Either[E1, B1], C] = {
      val pullOuter: ZIO[R1, Either[E1, B1], Unit] = ZIO.uninterruptibleMask { restore =>
        restore(as).flatMap { a =>
          (for {
            reservation <- f0(a).process.reserve
            control     <- restore(reservation.acquire)
            _           <- finalizer.set(reservation.release)
            _           <- currPull.set(Some(control.pull))
            _           <- currCmd.set(Some(control.command))
          } yield ())
        }
      }

      def pullInner: ZIO[R1, Either[E1, B1], C] =
        currPull.get.flatMap(
          _.fold(
            pullOuter.foldCauseM(
              Cause.sequenceCauseEither(_).fold(Pull.endNow, Pull.haltNow),
              _ => pullInner
            )
          )(
            _.foldCauseM(
              Cause
                .sequenceCauseEither(_)
                .fold(_ => currCmd.set(None) *> currPull.set(None) *> pullInner, Pull.haltNow),
              Pull.emitNow(_)
            )
          )
        )

      pullInner
    }

    ZStream {
      for {
        currPull  <- Ref.make[Option[ZIO[R1, Either[E1, B1], C]]](None).toManaged_
        currCmd   <- Ref.make[Option[M1 => ZIO[R1, E1, Any]]](None).toManaged_
        control   <- self.process
        finalizer <- ZManaged.finalizerRef[R1](_ => UIO.unit)
        pull      = go(control.pull, finalizer, currPull, currCmd)
        cmd       = (m: M1) => currCmd.get.flatMap(_.getOrElse(control.command)(m))
      } yield Control(pull, cmd)
    }
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
   * Runs the stream and collects all of its elements in a list.
   *
   * Equivalent to `run(Sink.collectAll[A])`.
   *
   * @return an action that yields the list of elements in the stream
   */
  final def runCollect: ZIO[R, E, List[A]] = runQuery(ZSink.collectAll[A])

  /**
   * Runs the stream and collects all of its elements in a list.
   *
   * Equivalent to `run(Sink.collectAll[A])`.
   *
   * @return an action that yields the list of elements in the stream
   */
  final def runCollectManaged: ZManaged[R, E, List[A]] = runQueryManaged(ZSink.collectAll[A])

  /**
   * Runs the stream purely for its effects. Any elements emitted by
   * the stream are discarded.
   *
   * @return a managed effect that drains the stream
   */
  final def runDrainManaged: ZManaged[R, E, Unit] = runQueryManaged(ZSink.drain)

  /**
   * Runs the stream purely for its effects. Any elements emitted by
   * the stream are discarded.
   *
   * @return an action that drains the stream
   */
  final def runDrain: ZIO[R, E, Unit] = runDrainManaged.use(UIO.succeedNow)

  /**
   * Runs the sink on the stream to produce either the sink's internal state or an error.
   *
   * @tparam B the internal state of the sink
   * @param sink the sink to run against
   * @return the internal state of the sink after exhausting the stream
   */
  def runQuery[R1 <: R, E1 >: E, A1 >: A, B](sink: ZSink[R1, E1, B, A1, Any]): ZIO[R1, E1, B] =
    runQueryManaged(sink).use(UIO.succeedNow)

  /**
   * Runs the sink on the stream to produce either the sink's internal state or an error.
   *
   * @tparam B the internal state of the sink
   * @param sink the sink to run against
   * @return the internal state of the sink after exhausting the stream wrapped in a managed resource
   */
  def runQueryManaged[R1 <: R, E1 >: E, A1 >: A, B](sink: ZSink[R1, E1, B, A1, Any]): ZManaged[R1, E1, B] =
    for {
      command <- self.process
      control <- sink.process
      go = {
        def pull: ZIO[R1, E1, B] =
          command.pull.foldM(
            _.fold(ZIO.failNow, _ => control.query),
            control.push(_).catchAll(_.fold(ZIO.failNow, _ => ZIO.unit)) *> pull
          )
        pull
      }
      b <- go.toManaged_
    } yield b

  /**
   * Takes the specified number of elements from this stream.
   * @param n the number of elements to retain
   * @return a stream with `n` or less elements
   */
  def take(n: Long): ZStream[R, E, M, Option[B], A] =
    ZStream {
      for {
        as      <- self.process
        counter <- Ref.make(0L).toManaged_
        pull = counter.get.flatMap { c =>
          if (c >= n) Pull.end(None)
          else as.pull.mapError(_.map(Some(_))) <* counter.set(c + 1)
        }
      } yield Control(pull, as.command)
    }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  final def takeWhile(pred: A => Boolean): ZStream[R, E, M, Option[B], A] =
    ZStream {
      for {
        done    <- Ref.make(false).toManaged_
        control <- self.process
        pull = done.get.flatMap {
          if (_)
            Pull.end(None)
          else
            control.pull.mapError(_.map(Some(_))).flatMap(a => if (pred(a)) Pull.emit(a) else done.set(true) *> Pull.end(None))
        }
      } yield Control(pull, control.command)
    }

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
     * Creates a stream from an effect and a finalizer. The finalizer will run
   * once the stream consumption has ended.
   *
   * @tparam R the environment required by the effects
   * @tparam E the error type yielded by the effect
   * @tparam A the value type yielded by the effect
   * @param acquire the effect
   * @param release the finalizer
   * @return a stream that emits the value yielded by the effect
   */
  def bracket[R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, Any]): ZStream[R, E, Any, Unit, A] =
    managed(ZManaged.make(acquire)(release))

  /**
   * Creates a stream from an effect and a finalizer. The finalizer will run
   * once the stream consumption has ended.
   *
   * @tparam R the environment required by the effects
   * @tparam E the error type yielded by the effect
   * @tparam A the value type yielded by the effect
   * @param acquire the effect
   * @param release the finalizer
   * @return a stream that emits the value yielded by the effect
   */
  def bracketExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R, Nothing, Any]): ZStream[R, E, Any, Unit, A] =
    managed(ZManaged.makeExit(acquire)(release))

  /**
   * The stream that always dies with the `ex`.
   * 
   * @param ex The exception that kills the stream
   * @return a stream that dies with an exception
   */
  def die(ex: => Throwable): UStream[Nothing] =
    halt(Cause.die(ex))

  /**
   * The stream that always dies with an exception described by `msg`.
   * 
   * @param msg The message to feed the runtime exception
   * @return a stream that dies with a runtime exception
   */
  def dieMessage(msg: => String): UStream[Nothing] =
    halt(Cause.die(new RuntimeException(msg)))

  /**
   * The empty stream
   */
  val empty: UStream[Nothing] =
    ZStream(Managed.succeedNow(Control(Pull.endUnit, Command.noop)))

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   *
   * @tparam A the value type
   * @param c a chunk of values
   * @return a finite stream of values
   */
  def fromChunk[A](c: => Chunk[A]): UStream[A] =
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
   * The stream that always halts with `cause`.
   * 
   * @tparam The error type
   * @param the cause for halting the stream
   * @return a stream that is halted
   */
  def halt[E](cause: => Cause[E]): Stream[E, Nothing] =
    fromEffect(ZIO.halt(cause))

  /**
   * The infinite stream of iterative function application: a, f(a), f(f(a)), f(f(f(a))), ...
   */
  def iterate[A](a: A)(f: A => A): UStream[A] =
    ZStream {
      Managed.effectTotal {
        var currA = a
        Control(
          ZIO.effectTotal { 
            val ret = currA
            currA = f(currA)
            ret
          },
          Command.noop
        )
      }
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

  /**
   * The stream that never produces any value or fails with any error.
   */
  val never: UStream[Nothing] =
    ZStream(ZManaged.succeedNow(Control(UIO.never, Command.noop)))

  /**
   * Constructs a stream from a range of integers (lower bound included, upper bound not included)
   * 
   * @param min the lower bound
   * @param max the upper bound
   */
  def range(min: Int, max: Int): ZStream[Any, Nothing, Any, Option[Unit], Int] =
    iterate(min)(_ + 1).takeWhile(_ < max)

  /**
   * Creates a single-valued pure stream
   *
   * @tparam A the value type
   * @param a the only value of the stream
   * @return a single-valued stream
   */
  def succeedNow[A](a: A): UStream[A] =
    managed(Managed.succeedNow(a))
}

package zio.stream.experimental

import zio._

abstract class ZSink[-R, +E, I, +Z] private (
  val push: ZManaged[R, Nothing, ZSink.Push[R, E, I, Z]]
) { self =>
  import ZSink.Push

  /**
   * Transforms this sink's result.
   */
  def map[Z2](f: Z => Z2): ZSink[R, E, I, Z2] =
    ZSink[R, E, I, Z2](self.push.map(_.mapResult(_.mapError(_.right.map(f)))))

  /**
   * Repeatedly runs the sink on the incoming elements for as long as they satisfy
   * the predicate `p`. The sink's results will be accumulated using the stepping
   * function `f`.
   */
  def collectAllWhileWith[S](z: S)(p: I => Boolean)(f: (S, Z) => S): ZSink[R, E, I, S] =
    ZSink {
      for {
        push                <- self.push
        sinkFuncAndRestart  <- ZSink.restartableSinkFunction(push.push)
        (sinkFunc, restart) = sinkFuncAndRestart
        state               <- Ref.make(z).toManaged_
        foldingPush <- Push.fromSinkFunction[I] { input =>
                        val (step, done) = input match {
                          case None => sinkFunc(None) -> true
                          case Some(is) =>
                            val filtered = is.takeWhile(p)

                            if (filtered.size < is.size) sinkFunc(Some(filtered)) -> true
                            else sinkFunc(Some(filtered))                         -> false
                        }

                        step.catchAll {
                          case Left(e)  => ZIO.fail(Left(e))
                          case Right(z) => state.update(f(_, z)) *> restart
                        } *> (if (done) state.get.flatMap(s => ZIO.fail(Right(s)))
                              else ZIO.unit)

                      }
      } yield foldingPush
    }

  /**
   * Converts this sink to a transducer that feeds incoming elements to the sink
   * and emits the sink's results as outputs. The sink will be restarted when
   * it ends.
   */
  def toTransducer: ZTransducer[R, E, I, Z] =
    ZTransducer {
      for {
        push                <- self.push
        sinkFuncAndRestart  <- ZSink.restartableSinkFunction(push.push)
        (sinkFunc, restart) = sinkFuncAndRestart
        transduce = (input: Option[Chunk[I]]) =>
          sinkFunc(input).foldM(
            {
              case Left(e)  => ZIO.fail(Left(e))
              case Right(z) => restart.as(Chunk.single(z))
            },
            _ => UIO.succeed(Chunk.empty)
          )
      } yield transduce
    }

  /**
   * Creates a sink that produces values until one verifies
   * the predicate `f`.
   */
  def untilOutputM[R1 <: R, E1 >: E](f: Z => ZIO[R1, E1, Boolean]): ZSink[R1, E1, I, Z] =
    ZSink {
      for {
        push                <- self.push
        sinkFuncAndRestart  <- ZSink.restartableSinkFunction(push.push)
        (sinkFunc, restart) = sinkFuncAndRestart
        newPush <- Push.fromSinkFunction[I] { is =>
                    sinkFunc(is).catchAll {
                      case Left(e) => ZIO.fail(Left(e))
                      case Right(z) =>
                        f(z).mapError(Left(_)) flatMap {
                          if (_) ZIO.fail(Right(z))
                          else restart
                        }

                    }
                  }
      } yield newPush
    }
}

object ZSink {
  type SinkFunction[-R, +E, I, +Z] = Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]

  case class Push[-R, +E, I, +Z](
    remainders: Ref[Chunk[I]],
    push: ZManaged[R, Nothing, SinkFunction[R, E, I, Z]]
  ) { self =>
    def mapResult[R2 <: R, E2, Z2](
      f: ZIO[R, Either[E, Z], Unit] => ZIO[R2, Either[E2, Z2], Unit]
    ): Push[R2, E2, I, Z2] =
      Push(remainders, push.map(_.andThen(f)))
  }

  object Push {
    def emit[Z](z: Z): IO[Either[Nothing, Z], Nothing]        = IO.fail(Right(z))
    def fail[E](e: E): IO[Either[E, Nothing], Nothing]        = IO.fail(Left(e))
    def halt[E](c: Cause[E]): IO[Either[E, Nothing], Nothing] = IO.halt(c).mapError(Left(_))

    def make[I]: Make[I] =
      new Make[I]

    class Make[I] {
      def apply[R, E, Z](
        f: (UIO[Chunk[I]], Chunk[I] => UIO[Unit]) => ZManaged[R, Nothing, SinkFunction[R, E, I, Z]]
      ): ZManaged[R, Nothing, Push[R, E, I, Z]] =
        ZRef
          .makeManaged[Chunk[I]](Chunk.empty)
          .map { ref =>
            val pop = ref.getAndSet(Chunk.empty)
            val log = (is: Chunk[I]) => ref.update(is ++ _)

            Push(
              ref,
              f(pop, log).map { outer => inputs =>
                inputs match {
                  case Some(is) => pop.flatMap(remainders => outer(Some(remainders ++ is)))
                  case None =>
                    pop.flatMap(remainders => outer(Some(remainders)).when(remainders.nonEmpty) *> outer(None))
                }
              }
            )
          }
    }

    def fromRemaindersFunction[I]: FromRemaindersFunction[I] =
      new FromRemaindersFunction[I]

    class FromRemaindersFunction[I] {
      def apply[R, E, Z](
        f: (UIO[Chunk[I]], Chunk[I] => UIO[Unit]) => SinkFunction[R, E, I, Z]
      ): ZManaged[R, Nothing, Push[R, E, I, Z]] =
        make[I]((pop, log) => ZManaged.succeed(f(pop, log)))
    }

    def fromSinkFunction[I]: FromSinkFunction[I] =
      new FromSinkFunction[I]

    class FromSinkFunction[I] {
      def apply[R, E, Z](
        f: SinkFunction[R, E, I, Z]
      ): ZManaged[R, Nothing, Push[R, E, I, Z]] =
        make[I]((_, _) => ZManaged.succeed(f))
    }

    def fromStatefulSinkFunction[I]: FromStatefulSinkFunction[I] =
      new FromStatefulSinkFunction[I]

    class FromStatefulSinkFunction[I] {
      def apply[R, E, Z](f: ZManaged[R, Nothing, SinkFunction[R, E, I, Z]]): ZManaged[R, Nothing, Push[R, E, I, Z]] =
        make[I]((_, _) => f)
    }

  }

  /**
   * Decorates a SinkFunction with a ZIO value that re-initializes it with a fresh state.
   */
  def restartableSinkFunction[R, E, I, Z](
    push: ZManaged[R, Nothing, SinkFunction[R, E, I, Z]]
  ): ZManaged[R, Nothing, (SinkFunction[R, E, I, Z], ZIO[R, Nothing, Unit])] =
    for {
      switchSink  <- ZManaged.switchable[R, Nothing, SinkFunction[R, E, I, Z]]
      initialPush <- switchSink(push).toManaged_
      currPush    <- Ref.make(initialPush).toManaged_
      restart     = switchSink(push).flatMap(currPush.set)
      newPush     = (input: Option[Chunk[I]]) => currPush.get.flatMap(_.apply(input))
    } yield (newPush, restart)

  def apply[R, E, I, Z](push: ZManaged[R, Nothing, Push[R, E, I, Z]]) =
    new ZSink(push) {}

  /**
   * A sink that collects all of its inputs into a list.
   */
  def collectAll[A]: ZSink[Any, Nothing, A, List[A]] =
    ZSink {
      Push.fromStatefulSinkFunction[A] {
        for {
          as <- ZRef.makeManaged[Chunk[A]](Chunk.empty)
          push = (xs: Option[Chunk[A]]) =>
            xs match {
              case Some(xs) => as.update(_ ++ xs)
              case None     => as.get.flatMap(as => Push.emit(as.toList))
            }
        } yield push
      }
    }

  /**
   * A sink that counts the number of elements fed to it.
   */
  val count: ZSink[Any, Nothing, Any, Long] =
    fold(0L)((s, _) => s + 1)

  def fromPush[R, E, I, Z](push: Push[R, E, I, Z]): ZSink[R, E, I, Z] =
    ZSink(Managed.succeed(push))

  /**
   * A sink that immediately ends with the specified value.
   */
  def succeed[Z](z: Z): ZSink[Any, Nothing, Any, Z] =
    ZSink {
      Push.fromSinkFunction[Any] {
        case Some(_) => UIO.unit
        case None    => ZIO.fail(Right(z))
      }
    }

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    ZSink {
      Push.fromStatefulSinkFunction[I] {
        for {
          state <- Ref.make(z).toManaged_
          push = (is: Option[Chunk[I]]) =>
            is match {
              case None => state.get.flatMap(s => ZIO.fail(Right(s)))
              case Some(is) =>
                state.get
                  .flatMap(is.foldM(_)(f))
                  .flatMap(state.set)
                  .mapError(Left(_))
            }
        } yield push
      }
    }

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def fold[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    foldM(z)((s, i) => UIO.succeedNow(f(s, i)))

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, Z](b: => ZIO[R, E, Z]): ZSink[R, E, Any, Z] =
    ZSink(Push.fromSinkFunction[Any] {
      case None => b.foldM(Push.fail, Push.emit)
      case _    => b.foldM(Push.fail, _ => UIO.unit)
    })

  /**
   * Collects the sink's inputs into a chunk for as long as the
   * elements satisfy a predicate.
   */
  def readWhile[I](p: I => Boolean): ZSink[Any, Nothing, I, Chunk[I]] =
    ZSink {
      Push.make[I] { (_, log) =>
        ZRef.makeManaged[Chunk[I]](Chunk.empty).map { acc => inputs =>
          inputs match {
            case None => acc.get.map(Right(_)).flip
            case Some(is) =>
              val (l, r) = is.splitWhere(!p(_))

              if (l.size == is.size) acc.update(_ ++ l)
              else log(r) *> acc.get.map(acc => Right(acc ++ l)).flip
          }
        }
      }
    }
}

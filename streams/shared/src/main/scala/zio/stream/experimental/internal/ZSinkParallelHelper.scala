package zio.stream.experimental.internal

import zio.stream.experimental.ZSink
import zio.stream.experimental.ZSink.Push
import zio.{ Fiber, UIO, ZIO, ZRef }

/**
 * Utility class that does common heavy lifting
 * for `ZSink.raceBoth` and `ZSink.zipWithPar`
 */
object ZSinkParallelHelper {
  case class State[Z1, Z2, E](left: Option[Either[E, Z1]], right: Option[Either[E, Z2]])

  private class Helper[R, E, I, Z1, Z2, Z](extract: State[Z1, Z2, E] => ZIO[R, Either[E, Z], Unit]) {
    private def interruptIfNeeded(f: Option[Fiber[_, _]]): UIO[Any] = f.fold[UIO[Any]](UIO.unit)(_.interrupt)

    private def applyRightFiber(
      s: State[Z1, Z2, E],
      pending: Option[Fiber[Nothing, Either[Either[E, Z2], Unit]]]
    ): ZIO[R, Either[E, Z], State[Z1, Z2, E]] =
      pending match {
        case Some(f) => f.join.flatMap(applyRightResult(s, _, None))
        case None    => ZIO.succeedNow(s)
      }

    private def applyLeftFiber(
      s: State[Z1, Z2, E],
      pending: Option[Fiber[Nothing, Either[Either[E, Z1], Unit]]]
    ): ZIO[R, Either[E, Z], State[Z1, Z2, E]] =
      pending match {
        case Some(f) => f.join.flatMap(applyLeftResult(s, _, None))
        case None    => ZIO.succeedNow(s)
      }

    def applyLeftResult(
      s: State[Z1, Z2, E],
      leftResult: Either[Either[E, Z1], Unit],
      pending: Option[Fiber[Nothing, Either[Either[E, Z2], Unit]]]
    ): ZIO[R, Either[E, Z], State[Z1, Z2, E]] =
      leftResult match {
        case Left(v) => {
          val newState: State[Z1, Z2, E] = s.copy(left = Some(v))
          extract(newState).foldM(
            e => interruptIfNeeded(pending) *> ZIO.fail(e),
            _ => applyRightFiber(newState, pending)
          )
        }
        case Right(_) => applyRightFiber(s, pending)
      }

    def applyRightResult(
      s: State[Z1, Z2, E],
      rightResult: Either[Either[E, Z2], Unit],
      pending: Option[Fiber[Nothing, Either[Either[E, Z1], Unit]]]
    ): ZIO[R, Either[E, Z], State[Z1, Z2, E]] =
      rightResult match {
        case Left(v) => {
          val newState: State[Z1, Z2, E] = s.copy(right = Some(v))
          extract(newState).foldM(
            e => interruptIfNeeded(pending) *> ZIO.fail(e),
            _ => applyLeftFiber(newState, pending)
          )
        }
        case Right(_) => applyLeftFiber(s, pending)
      }
  }

  /**
   * Runs two sinks in parallel and keeps track of sink's states.
   * At each step tries to resolve the final value of type `Z`.
   */
  def runBoth[R, E, I, Z1, Z2, Z](one: ZSink[R, E, I, Z1], another: ZSink[R, E, I, Z2])(
    extract: State[Z1, Z2, E] => ZIO[R, Either[E, Z], Unit]
  ): ZSink[R, E, I, Z] = {
    val helper = new Helper(extract)
    ZSink(for {
      ref <- ZRef.makeManaged[State[Z1, Z2, E]](State(None, None))
      p1  <- one.push
      p2  <- another.push
      push: Push[R, E, I, Z] = {
        in =>
          ref.get.flatMap {
            state =>
              val resultOrNewState: ZIO[R, Either[E, Z], State[Z1, Z2, E]] = {
                if (state.left.isEmpty) {
                  if (state.right.isEmpty) {
                    p1(in).either.raceWith(p2(in).either)(
                      (res1, fib2) =>
                        res1.foldM(
                          crash => fib2.interrupt *> ZIO.halt(crash),
                          ok => helper.applyLeftResult(state, ok, Some(fib2))
                        ),
                      (res2, fib1) =>
                        res2.foldM(
                          crash => fib1.interrupt *> ZIO.halt(crash),
                          ok => helper.applyRightResult(state, ok, Some(fib1))
                        )
                    )
                  } else {
                    p1(in).either.flatMap(helper.applyLeftResult(state, _, None))
                  }
                } else {
                  p2(in).either.flatMap(helper.applyRightResult(state, _, None))
                }
              }

              resultOrNewState.tap(newState => if (newState eq state) UIO.unit else ref.set(newState)).unit
          }
      }
    } yield push)
  }
}

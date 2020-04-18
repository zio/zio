package zio.stream.experimental.internal

import zio.stream.experimental.ZSink
import zio.stream.experimental.ZSink.Push
import zio.{ Fiber, UIO, ZIO, ZRef }

/**
 * Utility class that does common heavy lifting
 * for `ZSink.raceBoth` and `ZSink.zipWithPar`
 */
object ZSinkParallelHelper {
  case class Results[Z1, Z2, E](left: Option[Either[E, Z1]], right: Option[Either[E, Z2]])

  /**
   * Runs two sinks in parallel and keeps track of sink's states.
   * At each step tries to resolve the final value of type `Z`.
   */
  def runBoth[R, E, I, Z1, Z2, Z](one: ZSink[R, E, I, Z1], another: ZSink[R, E, I, Z2])(
    extract: Results[Z1, Z2, E] => ZIO[R, Either[E, Z], Unit]
  ): ZSink[R, E, I, Z] =
    ZSink(for {
      ref <- ZRef.makeManaged[Results[Z1, Z2, E]](Results(None, None))
      p1  <- one.push
      p2  <- another.push
      push: Push[R, E, I, Z] = {
        in =>
          ref.get.flatMap {
            state =>
              def appLyLeftResult(
                s: Results[Z1, Z2, E],
                leftResult: Either[Either[E, Z1], Unit],
                pending: Option[Fiber[Nothing, Either[Either[E, Z2], Unit]]]
              ): ZIO[R, Either[E, Z], Results[Z1, Z2, E]] =
                leftResult match {
                  case Left(v) => {
                    val newState: Results[Z1, Z2, E] = s.copy(left = Some(v))
                    extract(newState).foldM(
                      e => (pending.map(_.interrupt).getOrElse(UIO.unit)) *> ZIO.fail(e),
                      _ =>
                        pending match {
                          case Some(f) => f.join.flatMap(res2 => applyRightResult(newState, res2, None))
                          case None    => ZIO.succeedNow(newState)
                        }
                    )
                  }
                  case Right(_) => {
                    pending match {
                      case Some(f) => f.join.flatMap(res2 => applyRightResult(state, res2, None))
                      case None    => ZIO.succeedNow(state)
                    }
                  }
                }

              def applyRightResult(
                s: Results[Z1, Z2, E],
                rightResult: Either[Either[E, Z2], Unit],
                pending: Option[Fiber[Nothing, Either[Either[E, Z1], Unit]]]
              ): ZIO[R, Either[E, Z], Results[Z1, Z2, E]] =
                rightResult match {
                  case Left(v) => {
                    val newState: Results[Z1, Z2, E] = s.copy(right = Some(v))
                    extract(newState).foldM(
                      e => (pending.map(_.interrupt).getOrElse(UIO.unit)) *> ZIO.fail(e),
                      _ =>
                        pending match {
                          case Some(f) => f.join.flatMap(res1 => appLyLeftResult(newState, res1, None))
                          case None    => ZIO.succeedNow(newState)
                        }
                    )
                  }
                  case Right(_) => {
                    pending match {
                      case Some(f) => f.join.flatMap(res1 => appLyLeftResult(state, res1, None))
                      case None    => ZIO.succeedNow(state)
                    }
                  }
                }

              val emitOrNewState: ZIO[R, Either[E, Z], Results[Z1, Z2, E]] = {
                if (state.left.isEmpty) {
                  if (state.right.isEmpty) {
                    p1(in).either.raceWith(p2(in).either)(
                      (res1, fib2) =>
                        res1.foldM(
                          crash => fib2.interrupt *> ZIO.halt(crash),
                          ok => appLyLeftResult(state, ok, Some(fib2))
                        ),
                      (res2, fib1) =>
                        res2.foldM(
                          crash => fib1.interrupt *> ZIO.halt(crash),
                          ok => applyRightResult(state, ok, Some(fib1))
                        )
                    )
                  } else {
                    p1(in).either.flatMap(appLyLeftResult(state, _, None))
                  }
                } else {
                  p2(in).either.flatMap(applyRightResult(state, _, None))
                }
              }

              emitOrNewState.tap(newState => if (newState eq state) UIO.unit else ref.set(newState)).unit
          }
      }
    } yield push)
}

package zio

import zio.duration.Duration
import zio.internal.{FiberId, FiberRefs, Scheduler}

/**
 * Core ZIO type and its operations.
 */
object ZIO {
  // ... many existing definitions ...

  /**
   * Returns an effect that mirrors the success of this effect, but will timeout after the
   * specified `duration`. If the timeout expires, the provided `fallback` effect will be run
   * instead. The result is an `Either` where `Left` contains the original success value and
   * `Right` contains the fallback value.
   *
   * This implementation adds a fast‑path for infinite durations to avoid the overhead of
   * spawning a timeout fiber when no timeout is required.
   */
  final def timeoutTo[R, E, A, B](
    self: ZIO[R, E, A],
    duration: Duration,
    fallback: => ZIO[R, E, B]
  ): ZIO[R, E, Either[A, B]] = {
    // Fast‑path: an infinite timeout means we never need to schedule a timeout.
    // This avoids the overhead of creating a second fiber.
    if (duration.isInfinite) {
      self.map(Left(_))
    } else {
      // Original implementation based on `raceFibersWith`.
      ZIO.uninterruptibleMask { restore =>
        restore(self).raceWith(
          ZIO.sleep(duration) *> fallback.map(Right(_))
        )(
          (left, right) => left.map(Left(_)).orElse(right),
          (right, left) => right.map(Right(_)).orElse(left)
        )
      }
    }
  }

  // ... many existing definitions ...
}

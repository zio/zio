package zio

import java.util.concurrent.atomic.AtomicBoolean
import zio.internal.{FiberRunnable, FiberScope}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A simplified optimized race implementation that reuses the calling fiber for one side of the race,
 * reducing overhead by creating only one new fiber instead of two.
 */
object SimpleOptimizedRace {

  /**
   * An optimized version of `race` that reuses the calling fiber for the left side of the race,
   * creating only one new fiber for the right side. This implementation reduces allocations and
   * improves interrupt handling for better performance.
   *
   * This implementation uses an AtomicBoolean for tracking the winner instead of a Ref for better
   * performance and to avoid potential deadlocks in concurrent completion scenarios.
   */
  def race[R, E, A](
    left: ZIO[R, E, A],
    right: ZIO[R, E, A]
  ): ZIO[R, E, A] = {
    ZIO.uninterruptibleMask { restore =>
      for {
        // Create a promise to hold the result
        promise <- Promise.make[E, A]
        // Use AtomicBoolean instead of Ref for better performance
        winnerRef = new AtomicBoolean(false)
        
        // Start the right side in a separate fiber
        rightFiber <- restore(right).fork
        
        // Set up an observer for the right fiber that completes when the right side completes
        // This avoids creating an additional fiber for monitoring
        _ <- ZIO.succeed {
          rightFiber.addObserver { exit =>
            // Try to set the winner flag atomically
            if (winnerRef.compareAndSet(false, true)) {
              // If we won the race, complete the promise with the right side's result
              exit match {
                case Exit.Success(value) => Unsafe.unsafe { implicit unsafe =>
                  Runtime.default.unsafe.run(promise.succeed(value))
                }
                case Exit.Failure(cause) => Unsafe.unsafe { implicit unsafe =>
                  Runtime.default.unsafe.run(promise.failCause(cause))
                }
              }
            }
          }
        }
        
        // Run the left side directly in the current fiber
        leftExit <- restore(left).exit
        
        // Try to set the winner flag atomically for the left side
        _ <- ZIO.succeed {
          if (winnerRef.compareAndSet(false, true)) {
            // If left side won, complete the promise and interrupt the right fiber
            leftExit match {
              case Exit.Success(value) => 
                Unsafe.unsafe { implicit unsafe =>
                  Runtime.default.unsafe.run(promise.succeed(value))
                  // Interrupt the right fiber immediately if left won
                  Runtime.default.unsafe.run(rightFiber.interrupt)
                }
              case Exit.Failure(cause) => 
                Unsafe.unsafe { implicit unsafe =>
                  Runtime.default.unsafe.run(promise.failCause(cause))
                  // Still need to interrupt the right fiber on failure
                  Runtime.default.unsafe.run(rightFiber.interrupt)
                }
            }
          }
        }
        
        // Wait for the result
        result <- promise.await
      } yield result
    }
  }
}
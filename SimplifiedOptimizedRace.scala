package zio

import zio.internal.{FiberRunnable, FiberScope}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simplified optimized race implementation that reuses the calling fiber for one side of the race,
 * reducing overhead by creating only one new fiber instead of two.
 */
object SimplifiedOptimizedRace {

  /**
   * An optimized version of `race` that reuses the calling fiber for the left side of the race,
   * creating only one new fiber for the right side. This implementation reduces allocations and
   * improves interrupt handling for better performance.
   */
  def race[R, E, A](
    left: ZIO[R, E, A],
    right: ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (parentFiber, parentStatus) =>
      val graft = ZIO.Grafter(parentFiber)
      implicit val unsafe: Unsafe = Unsafe.unsafe

      val parentRuntimeFlags = parentStatus.runtimeFlags
      val raceIndicator = new AtomicBoolean(true)
      val parentFiberId = parentFiber.id

      // Create only one fiber for the right side
      val rightFiber = ZIO.unsafe.makeChildFiber(trace, right, parentFiber, parentRuntimeFlags, FiberScope.global)

      ZIO.async[R, E, A](
        { cb =>
          // Set up observer for the right fiber - optimized to reduce allocations
          rightFiber.unsafe.addObserver { rightExit =>
            if (raceIndicator.compareAndSet(true, false)) {
              // Inline the fold to reduce closure allocations
              rightExit.foldExit(
                cause => cb(ZIO.failCause(cause.asInstanceOf[Cause[E]])),
                value => cb(rightFiber.inheritAll *> ZIO.succeed(value))
              )
            }
          }

          // Start the right fiber
          rightFiber.unsafe.start()

          // Execute the left side directly in the calling fiber
          val leftEffect = graft.applyOnExit(left)
          
          // Execute the left effect directly in the current fiber
          parentFiber.unsafeRunEffect(leftEffect).fold(
              cause => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side failed, join right fiber to combine causes
                  cb(rightFiber.join.mapErrorCause(joinCause => cause && joinCause))
                }
              },
              value => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side succeeded, interrupt right fiber
                  cb(rightFiber.interruptAs(parentFiberId).as(value))
                }
              }
            )
          }
        },
        parentFiber.id <> rightFiber.id
      )
    }

  /**
   * An optimized version of `raceFirst` that reuses the calling fiber for the left side of the race,
   * creating only one new fiber for the right side. This implementation avoids unnecessary exit/unexit
   * operations and reduces allocations for better performance.
   */
  def raceFirst[R, E, A](
    left: ZIO[R, E, A],
    right: ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (parentFiber, parentStatus) =>
      val graft = ZIO.Grafter(parentFiber)
      implicit val unsafe: Unsafe = Unsafe.unsafe

      val parentRuntimeFlags = parentStatus.runtimeFlags
      val raceIndicator = new AtomicBoolean(true)
      val parentFiberId = parentFiber.id

      // Create only one fiber for the right side
      val rightFiber = ZIO.unsafe.makeChildFiber(trace, right, parentFiber, parentRuntimeFlags, FiberScope.global)

      ZIO.async[R, E, A](
        { cb =>
          // Set up observer for the right fiber - optimized to reduce allocations
          rightFiber.unsafe.addObserver { rightExit =>
            if (raceIndicator.compareAndSet(true, false)) {
              // Simply return the exit result directly without additional transformations
              cb(ZIO.done(rightExit))
            }
          }

          // Start the right fiber
          rightFiber.unsafe.start()

          // Execute the left side directly in the calling fiber
          val leftEffect = graft.applyOnExit(left)
          
          // Execute the left effect directly in the current fiber
          parentFiber.unsafeRunEffect(leftEffect).fold(
              cause => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side failed, return the exit result and interrupt the right fiber
                  cb(rightFiber.interruptAs(parentFiberId) *> ZIO.failCause(cause))
                }
              },
              value => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side succeeded, return the exit result and interrupt the right fiber
                  cb(rightFiber.interruptAs(parentFiberId) *> ZIO.succeed(value))
                }
              }
            )
          }
        },
        parentFiber.id <> rightFiber.id
      )
    }
}
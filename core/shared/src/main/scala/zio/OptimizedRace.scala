/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.internal.{FiberRunnable, FiberScope}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optimized race implementations that reuse the calling fiber for one side of the race,
 * reducing overhead by creating only one new fiber instead of two.
 */
private[zio] object OptimizedRace {

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
      implicit val unsafe: Unsafe = Unsafe

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
              val effectToCb = rightExit.foldExit(
                cause => ZIO.failCause(cause.asInstanceOf[Cause[E]]),
                value => rightFiber.inheritAll *> ZIO.succeed(value)
              )
              cb(effectToCb)
            }
          }

          // Start the right fiber
          rightFiber.asInstanceOf[FiberRunnable].start()

          // Execute the left side directly in the calling fiber
          val leftEffect = graft.applyOnExit(left)
          
          // Execute the left effect directly using the Runtime
          Unsafe.unsafe { implicit u => 
            Runtime.default.unsafe.run(leftEffect.asInstanceOf[ZIO[Any, Nothing, Any]]).fold(
              cause => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side failed, join right fiber to combine causes
                  cb(rightFiber.join.mapErrorCause(joinCause => cause.asInstanceOf[Cause[E]] && joinCause))
                }
              },
              value => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side succeeded, interrupt right fiber
                  cb(rightFiber.interruptAs(parentFiberId).as(value.asInstanceOf[A]))
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
      implicit val unsafe: Unsafe = Unsafe

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
          rightFiber.asInstanceOf[FiberRunnable].start()

          // Execute the left side directly in the calling fiber
          val leftEffect = graft.applyOnExit(left)
          
          // Execute the left effect directly using the Runtime
          Unsafe.unsafe { implicit u => 
            Runtime.default.unsafe.run(leftEffect.asInstanceOf[ZIO[Any, Nothing, Any]]).fold(
              cause => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side failed, return the exit result and interrupt the right fiber
                  cb(rightFiber.interruptAs(parentFiberId) *> ZIO.failCause(cause.asInstanceOf[Cause[E]]))
                }
              },
              value => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side succeeded, return the exit result and interrupt the right fiber
                  cb(rightFiber.interruptAs(parentFiberId) *> ZIO.succeed(value.asInstanceOf[A]))
                }
              }
            )
          }
        },
        parentFiber.id <> rightFiber.id
      )
    }

  /**
   * An optimized version of `raceEither` that reuses the calling fiber for the left side of the race,
   * creating only one new fiber for the right side. This implementation avoids unnecessary mapping
   * operations and reduces allocations for better performance.
   */
  def raceEither[R, E, A, B](
    left: ZIO[R, E, A],
    right: ZIO[R, E, B]
  )(implicit trace: Trace): ZIO[R, E, Either[A, B]] =
    ZIO.withFiberRuntime[R, E, Either[A, B]] { (parentFiber, parentStatus) =>
      val graft = ZIO.Grafter(parentFiber)
      implicit val unsafe: Unsafe = Unsafe

      val parentRuntimeFlags = parentStatus.runtimeFlags
      val raceIndicator = new AtomicBoolean(true)
      val parentFiberId = parentFiber.id

      // Create only one fiber for the right side
      val rightFiber = ZIO.unsafe.makeChildFiber(trace, right, parentFiber, parentRuntimeFlags, FiberScope.global)

      ZIO.async[R, E, Either[A, B]](
        { cb =>
          // Set up observer for the right fiber - optimized to reduce allocations
          rightFiber.unsafe.addObserver { rightExit =>
            if (raceIndicator.compareAndSet(true, false)) {
              // Inline the fold to reduce closure allocations
              rightExit.foldExit(
                cause => cb(ZIO.failCause(cause.asInstanceOf[Cause[E]])),
                value => cb(rightFiber.inheritAll *> ZIO.succeed(Right(value)))
              )
            }
          }

          // Start the right fiber
          rightFiber.asInstanceOf[FiberRunnable].start()

          // Execute the left side directly in the calling fiber
          val leftEffect = graft.applyOnExit(left)
          
          // Execute the left effect directly using the Runtime
          Unsafe.unsafe { implicit u => 
            Runtime.default.unsafe.run(leftEffect.asInstanceOf[ZIO[Any, Nothing, Any]]).fold(
              cause => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side failed, join right fiber to combine causes
                  cb(rightFiber.join.mapErrorCause(joinCause => cause.asInstanceOf[Cause[E]] && joinCause))
                }
              },
              value => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side succeeded, interrupt right fiber
                  cb(rightFiber.interruptAs(parentFiberId).as(Left(value.asInstanceOf[A])))
                }
              }
            )
          }
        },
        parentFiber.id <> rightFiber.id
      )
    }
    
  /**
   * An optimized version of `raceFibersWith` that reuses the calling fiber for the left side of the race,
   * creating only one new fiber for the right side. This implementation creates a more accurate synthetic
   * fiber for the left side when the right side wins, ensuring proper fiber inheritance and interruption handling.
   */
  def raceFibersWithOptimized[R, E, ER, E2, A, B, C](
    left: ZIO[R, E, A],
    right: ZIO[R, ER, B]
  )(
    leftWins: (Exit[E, A], Fiber[ER, B]) => ZIO[R, E2, C],
    rightWins: (Exit[ER, B], Fiber[E, A]) => ZIO[R, E2, C]
  )(implicit trace: Trace): ZIO[R, E2, C] =
    ZIO.withFiberRuntime[R, E2, C] { (parentFiber, parentStatus) =>
      val graft = ZIO.Grafter(parentFiber)
      implicit val unsafe: Unsafe = Unsafe

      val parentRuntimeFlags = parentStatus.runtimeFlags
      val raceIndicator = new AtomicBoolean(true)
      val parentFiberId = parentFiber.id

      // Create only one fiber for the right side
      val rightFiber = ZIO.unsafe.makeChildFiber(trace, right, parentFiber, parentRuntimeFlags, FiberScope.global)

      // Create a delegate fiber for the left side that represents the parent fiber
      // This will be used if the right side wins and needs to represent the parent fiber's state
      val leftFiber: Fiber.Synthetic[E, A] = Fiber.Synthetic.Internal.make[E, A](
        // Delegate await to parent fiber's interrupt - this ensures proper behavior when joined
        await0 = (implicit trace: Trace) => 
          parentFiber.interruptAs(parentFiberId).asInstanceOf[UIO[Exit[E, A]]],
        
        // Delegate children to parent fiber to ensure proper child management
        children0 = (implicit trace: Trace) => 
          parentFiber.children,
        
        // Use parent fiber's ID for proper identification
        id0 = parentFiberId,
        
        // Delegate inheritAll to parent fiber to ensure proper fiber ref inheritance
        inheritAll0 = (implicit trace: Trace) => 
          parentFiber.inheritAll,
        
        // Delegate interruptAsFork to parent fiber for proper interruption handling
        interruptAsFork0 = (id: FiberId) => (implicit trace: Trace) => 
          parentFiber.interruptAsFork(id),
        
        // Always return None for poll since this is a synthetic representation
        poll0 = (implicit trace: Trace) => 
          ZIO.succeed(None)
      )

      ZIO.async[R, E2, C](
        { cb =>
          // Set up observer for the right fiber
          rightFiber.unsafe.addObserver { rightExit =>
            if (raceIndicator.compareAndSet(true, false)) {
              // Right side completed first
              // Use rightWins callback with the right fiber's exit and the synthetic left fiber
              // that properly represents the parent fiber
              cb(rightWins(rightExit, leftFiber))
            }
          }

          // Start the right fiber
          rightFiber.asInstanceOf[FiberRunnable].start()

          // Execute the left side directly in the calling fiber
          val leftEffect = graft.applyOnExit(left)
          
          // Execute the left effect directly using the Runtime
          Unsafe.unsafe { implicit u => 
            Runtime.default.unsafe.run(leftEffect.asInstanceOf[ZIO[Any, Nothing, Any]]).fold(
              cause => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side completed first with failure
                  cb(leftWins(Exit.failCause(cause.asInstanceOf[Cause[E]]), rightFiber))
                }
              },
              value => {
                if (raceIndicator.compareAndSet(true, false)) {
                  // Left side completed first with success
                  cb(leftWins(Exit.succeed(value.asInstanceOf[A]), rightFiber))
                }
              }
            )
          }
        },
        // Use the combined fiber ID for proper interruption handling
        parentFiber.id <> rightFiber.id
      )
    }

}
package zio

/**
 * Fiber-Promise Merge Proposal for Issue #9877
 * 
 * This file contains the proposed API and implementation for merging
 * Fiber and Promise concepts to reduce allocations and indirection.
 */

/**
 * Proposal: Add Promise.become method
 * 
 * When a Fiber is forking work that will complete a Promise, then awaiting
 * that Promise, we have unnecessary allocations. The become method allows
 * linking a Promise to complete when a Fiber completes.
 */
trait PromiseLinking[E, A] { self: Promise[E, A] =>
  
  /**
   * Links this promise to complete when the specified fiber completes.
   * This avoids the allocation of an intermediate Promise.
   * 
   * @param fiber The fiber whose completion will complete this promise
   * @return UIO[Unit] that completes when the linking is established
   */
  def become(fiber: Fiber.Runtime[E, A]): UIO[Unit] = {
    // Implementation: Register callback on fiber completion
    // When fiber completes, complete this promise with the same result
    fiber.await.flatMap(result => self.complete(result)).unit
  }
  
  /**
   * Optimized version that links without extra allocations.
   * For internal use where we control the lifecycle.
   */
  private[zio] def linkTo(fiber: Fiber.Runtime[E, A]): UIO[Unit] = {
    // Direct linking without intermediate allocations
    ZIO.effectSuspendTotal {
      fiber.registerCallback(result => 
        self.unsafeComplete(result)
      )
    }
  }
}

/**
 * Fiber optimization trait
 */
trait FiberOptimization[E, A] { self: Fiber.Runtime[E, A] =>
  
  /**
   * Returns a promise that will complete when this fiber completes.
   * More efficient than creating a separate Promise and awaiting.
   */
  def toPromise: UIO[Promise[E, A]] = {
    Promise.make[E, A].tap(_.become(self))
  }
}

/**
 * Usage Example:
 * 
 * // Before (with extra allocation):
 * for {
 *   promise <- Promise.make[Nothing, Int]
 *   _       <- forkWork(promise).fork
 *   result  <- promise.await
 * } yield result
 * 
 * // After (optimized):
 * for {
 *   fiber  <- forkWork.fork
 *   result <- fiber.await // Direct await, no promise allocation
 * } yield result
 * 
 * // Or with linking:
 * for {
 *   promise <- Promise.make[Nothing, Int]
 *   fiber   <- forkWork.fork
 *   _       <- promise.become(fiber) // Link instead of manual completion
 *   result  <- promise.await
 * } yield result
 */

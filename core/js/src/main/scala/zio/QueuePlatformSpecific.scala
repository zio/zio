package zio

private[zio] trait QueuePlatformSpecific {
  // java.util.concurrent.ConcurrentLinkedDeque is not available in ScalaJS or Scala Native, so we use an ArrayDeque instead
  /**
   * The deque in which a `Queue` keeps the promises of the fibers suspended in
   * `take` and, for a back-pressured queue, the values of the suspended
   * offers. It is
   * `java.util.concurrent.ConcurrentLinkedDeque` on the JVM,
   * `java.util.ArrayDeque` on Scala.js, and a `ConcurrentLinkedQueue` with an
   * added `addFirst` method on Scala Native.
   */
  type ConcurrentDeque[A] = java.util.ArrayDeque[A]
}

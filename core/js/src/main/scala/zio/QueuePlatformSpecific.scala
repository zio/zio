package zio

private[zio] trait QueuePlatformSpecific {
  // java.util.concurrent.ConcurrentLinkedDeque is not available in ScalaJS, so we use an ArrayDeque instead
  type ConcurrentDeque[A] = java.util.ArrayDeque[A]
}

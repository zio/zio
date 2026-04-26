package zio

private[zio] trait QueuePlatformSpecific {

  // java.util.concurrent.ConcurrentLinkedDeque is available in Scala Native since 0.5.6
  // (scala-native/scala-native#4046), so we can use the same type alias as the JVM port.
  type ConcurrentDeque[A] = java.util.concurrent.ConcurrentLinkedDeque[A]
}

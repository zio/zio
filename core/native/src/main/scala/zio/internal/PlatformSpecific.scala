package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.{HashMap, HashSet, Map => JMap, Set => JSet}

private[zio] trait PlatformSpecific {

  /**
   * Adds a shutdown hook that executes the specified action on shutdown.
   *
   * This is currently a no-op on Scala Native.
   */
  final def addShutdownHook(action: () => Unit): Unit = {
    val _ = action
  }

  /**
   * Adds a signal handler for the specified signal (e.g. "INFO"). This method
   * never fails even if adding the handler fails.
   *
   * This is currently a no-op on Scala Native.
   */
  final def addSignalHandler(signal: String, action: () => Unit): Unit = {
    val _ = signal
    val _ = action

    ()
  }

  /**
   * Exits the application with the specified exit code.
   */
  final def exit(code: Int): Unit = {
    val _ = code
  }

  /**
   * Returns the name of the thread group to which this thread belongs. This is
   * a side-effecting method.
   */
  final val getCurrentThreadGroup: String = ""

  /**
   * Returns whether the current platform is ScalaJS.
   */
  final val isJS = false

  /**
   * Returns whether the currently platform is the JVM.
   */
  final val isJVM = false

  /**
   * Returns whether the currently platform is Scala Native.
   */
  final val isNative = true

  final def newWeakSet[A](): JSet[A] = new HashSet[A]()

  final def newConcurrentSet[A](): JSet[A] = new HashSet[A]()

  final def newConcurrentWeakSet[A](): JSet[A] = new HashSet[A]()

  final def newWeakHashMap[A, B](): JMap[A, B] = new HashMap[A, B]()

  final def newWeakReference[A](value: A): () => A = { () => value }
}

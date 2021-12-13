package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.{HashMap, HashSet, Map => JMap, Set => JSet}

private[zio] trait PlatformSpecific {

  /**
   * Adds a shutdown hook that executes the specified action on shutdown.
   */
  def addShutdownHook(action: () => Unit): Unit = {
    val _ = action
  }

  def addSignalHandler(signal: String, action: () => Unit): Unit = {
    import org.scalajs.dom
    import org.scalajs.dom.raw._

    try {
      val string = s"zio-signal=${signal}".toLowerCase

      dom.window.onhashchange = (e: HashChangeEvent) => {
        if (e.newURL.toLowerCase.contains(string)) action()
      }
    } catch {
      case _: Throwable => ()
    }
  }

  /**
   * Exits the application with the specified exit code.
   */
  def exit(code: Int): Unit = {
    val _ = code
  }

  /**
   * Returns the name of the thread group to which this thread belongs. This is
   * a side-effecting method.
   */
  val getCurrentThreadGroup: String = ""

  /**
   * Returns whether the current platform is ScalaJS.
   */
  val isJS = true

  /**
   * Returns whether the currently platform is the JVM.
   */
  val isJVM = false

  /**
   * Returns whether the currently platform is Scala Native.
   */
  val isNative = false

  final def newWeakSet[A](): JSet[A] = new HashSet[A]()

  final def newConcurrentSet[A](): JSet[A] = new HashSet[A]()

  final def newConcurrentWeakSet[A](): JSet[A] = new HashSet[A]()

  final def newWeakHashMap[A, B](): JMap[A, B] = new HashMap[A, B]()

  final def newWeakReference[A](value: A): () => A = { () => value }
}

package java.util.concurrent

import java.util.{ Collection, List => JList }
import java.util.concurrent.{ Future => JFuture }

trait ExecutorService extends Executor {

  def shutdown(): Unit

  def shutdownNow(): JList[Runnable]

  def isShutdown(): Boolean

  def isTerminated(): Boolean

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean

  def submit[T](task: Callable[T]): JFuture[T]

  def submit[T](task: Runnable, result: T): JFuture[T]

  def submit(task: Runnable): JFuture[_]

  def invokeAll[T](tasks: Collection[_ <: Callable[T]]): JList[JFuture[T]]

  def invokeAll[T](tasks: Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): JList[JFuture[T]]

  def invokeAny[T](tasks: Collection[_ <: Callable[T]]): T

  def invokeAny[T](tasks: Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T
}

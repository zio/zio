package java.util.concurrent

import java.util

// only for linking Scala.js 1.x
trait ExecutorService extends Executor { // public interface

  def shutdown(): Unit

  def shutdownNow: List[Runnable]

  def isShutdown: Boolean

  def isTerminated: Boolean

  @throws[InterruptedException]
  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean

  def submit[T](task: Callable[T]): util.concurrent.Future[T]

  def submit[T](task: Runnable, result: T): util.concurrent.Future[T]

  def submit(task: Runnable): util.concurrent.Future[_]

  @throws[InterruptedException]
  def invokeAll[T](tasks: util.Collection[_ <: Callable[T]]): List[util.concurrent.Future[T]]

  @throws[InterruptedException]
  def invokeAll[T](
    tasks: util.Collection[_ <: Callable[T]],
    timeout: Long,
    unit: TimeUnit
  ): List[util.concurrent.Future[T]]

  @throws[InterruptedException]
  @throws[ExecutionException]
  def invokeAny[T](tasks: util.Collection[_ <: Callable[T]]): T

  @throws[InterruptedException]
  @throws[ExecutionException]
  @throws[TimeoutException]
  def invokeAny[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T
}

// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

trait ExecutionMetrics {

  /**
   * The concurrency level of the executor.
   */
  def concurrency: Int

  /**
   * The capacity of the executor.
   */
  def capacity: Int

  /**
   * The number of tasks remaining to be executed.
   */
  def size: Int

  /**
   * The number of tasks that have been enqueued, over all time.
   */
  def enqueuedCount: Long

  /**
   * The number of tasks that have been dequeued, over all time.
   */
  def dequeuedCount: Long
}

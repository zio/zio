/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio.internal

import java.util.concurrent.{
  LinkedBlockingQueue,
  RejectedExecutionException,
  SynchronousQueue,
  ThreadPoolExecutor,
  TimeUnit
}

private[internal] abstract class DefaultExecutors {

  lazy val blocking: Executor =
    Executor.fromThreadPoolExecutor(_ => Int.MaxValue) {
      val corePoolSize  = 0
      val maxPoolSize   = 1000
      val keepAliveTime = 60000L
      val timeUnit      = TimeUnit.MILLISECONDS
      val workQueue     = new SynchronousQueue[Runnable]()
      val threadFactory = new NamedThreadFactory("zio-default-blocking", true)

      val threadPool = new ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        keepAliveTime,
        timeUnit,
        workQueue,
        threadFactory
      )

      threadPool
    }

  final def makeDefault(yieldOpCount: Int): Executor =
    fromThreadPoolExecutor(_ => yieldOpCount) {
      val corePoolSize  = Runtime.getRuntime.availableProcessors() * 2
      val maxPoolSize   = corePoolSize
      val keepAliveTime = 60000L
      val timeUnit      = TimeUnit.MILLISECONDS
      val workQueue     = new LinkedBlockingQueue[Runnable]()
      val threadFactory = new NamedThreadFactory("zio-default-async", true)

      val threadPool = new ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        keepAliveTime,
        timeUnit,
        workQueue,
        threadFactory
      )
      threadPool.allowCoreThreadTimeOut(true)

      threadPool
    }

  final def fromThreadPoolExecutor(yieldOpCount0: ExecutionMetrics => Int)(
    es: ThreadPoolExecutor
  ): Executor =
    new Executor {
      private[this] def metrics0 = new ExecutionMetrics {
        def concurrency: Int = es.getMaximumPoolSize()

        def capacity: Int = {
          val queue = es.getQueue()

          val remaining = queue.remainingCapacity()

          if (remaining == Int.MaxValue) remaining
          else remaining + queue.size
        }

        def size: Int = es.getQueue().size

        def workersCount: Int = es.getPoolSize()

        def enqueuedCount: Long = es.getTaskCount()

        def dequeuedCount: Long = enqueuedCount - size.toLong
      }

      def metrics = Some(metrics0)

      def yieldOpCount = yieldOpCount0(metrics0)

      def submit(runnable: Runnable): Boolean =
        try {
          es.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }
    }
}

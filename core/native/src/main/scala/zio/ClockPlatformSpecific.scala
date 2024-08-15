/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{DurationSyntax => _}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

private[zio] trait ClockPlatformSpecific {
  import ClockPlatformSpecific.Timer
  private[zio] val globalScheduler = new Scheduler {
    import Scheduler.CancelToken

    private[this] val ConstTrue  = () => false
    private[this] val ConstFalse = () => false

    override def schedule(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): CancelToken =
      (duration: @unchecked) match {
        case zio.Duration.Zero =>
          task.run()
          ConstTrue
        case zio.Duration.Infinity =>
          ConstFalse
        case zio.Duration.Finite(nanos) =>
          var completed = false

          val handle = Timer.timeout(FiniteDuration(nanos, TimeUnit.NANOSECONDS)) { () =>
            completed = true

            task.run()
          }
          () => {
            handle.clear()
            !completed
          }
      }
  }
}

private object ClockPlatformSpecific {
  import scala.collection.mutable
  import scala.concurrent.Future
  import scala.scalanative.annotation.alwaysinline
  import scala.scalanative.libc.stdlib
  import scala.scalanative.loop.EventLoop
  import scala.scalanative.loop.LibUV._
  import scala.scalanative.loop.LibUVConstants._
  import scala.scalanative.runtime.Intrinsics._
  import scala.scalanative.runtime._
  import scala.scalanative.unsafe.Ptr

  private object HandleUtils {
    private val references = mutable.Map.empty[Object, Int]

    @alwaysinline def getData[T <: Object](handle: Ptr[Byte]): T = {
      // data is the first member of uv_loop_t
      val ptrOfPtr = handle.asInstanceOf[Ptr[Ptr[Byte]]]
      val dataPtr  = !ptrOfPtr
      if (dataPtr == null) null.asInstanceOf[T]
      else {
        val rawptr = toRawPtr(dataPtr)
        castRawPtrToObject(rawptr).asInstanceOf[T]
      }
    }
    @alwaysinline def setData(handle: Ptr[Byte], obj: Object): Unit = {
      // data is the first member of uv_loop_t
      val ptrOfPtr = handle.asInstanceOf[Ptr[Ptr[Byte]]]
      if (obj != null) {
        if (references.contains(obj)) references(obj) += 1
        else references(obj) = 1
        val rawptr = castObjectToRawPtr(obj)
        !ptrOfPtr = fromRawPtr[Byte](rawptr)
      } else {
        !ptrOfPtr = null
      }
    }
    private val onCloseCB: CloseCB = (handle: UVHandle) => {
      stdlib.free(handle)
    }

    @alwaysinline def close(handle: Ptr[Byte]): Unit =
      if (getData(handle) != null) {
        uv_close(handle, onCloseCB)
        val data    = getData[Object](handle)
        val current = references(data)
        if (current > 1) references(data) -= 1
        else references.remove(data)
        setData(handle, null)
      }
  }

  @alwaysinline final class Timer private (private val ptr: Ptr[Byte]) extends AnyVal {
    def clear(): Unit = {
      uv_timer_stop(ptr)
      HandleUtils.close(ptr)
    }
  }

  object Timer {
    // def delay(duration: FiniteDuration)(implicit trace: Trace): UIO[Unit] =
    //   for {
    //     promise <- Promise.make[Nothing, Unit]
    //     _       <- ZIO.succeed(timeout(duration)(() => promise.succeed(()).unit))
    //     _       <- promise.await
    //   } yield ()

    def delay(duration: FiniteDuration)(implicit trace: Trace): UIO[Unit] =
      for {
        promise <- Promise.make[Nothing, Unit]
        _       <- ZIO.succeed(timeoutWithTrace(duration)(() => promise.succeed(()).unit))
        _       <- promise.await
      } yield ()

    // New method added with Trace parameter for new functionality
    private def timeoutWithTrace(duration: FiniteDuration)(callback: () => Unit)(implicit trace: Trace): Timer = {
      val scheduledFuture = scheduler.schedule(
        new Runnable {
          override def run(): Unit = callback()
        },
        duration.toMillis,
        TimeUnit.MILLISECONDS
      )
      new Timer(scheduledFuture)
    }

    // Original method retained for backward compatibility
    def timeout(duration: FiniteDuration)(callback: () => Unit)(implicit unsafe: Unsafe): Timer =
      timeoutWithTrace(duration)(callback)(Trace.empty)

    def repeatWithTrace(duration: FiniteDuration)(callback: () => Unit)(implicit trace: Trace): Timer = {
      val scheduledFuture = scheduler.scheduleAtFixedRate(
        new Runnable {
          override def run(): Unit = callback()
        },
        duration.toMillis,
        duration.toMillis,
        TimeUnit.MILLISECONDS
      )
      new Timer(scheduledFuture)
    }

    def repeat(duration: FiniteDuration)(callback: () => Unit)(implicit unsafe: Unsafe): Timer =
      repeatWithTrace(duration)(callback)(Trace.empty)
  }
}

// /*
//  * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package zio

// import java.util.concurrent.{ScheduledExecutorService, Executors, TimeUnit}
// import zio.stacktracer.TracingImplicits.disableAutoTrace
// import zio.{DurationSyntax => _}

// import scala.concurrent.duration.FiniteDuration

// private[zio] trait ClockPlatformSpecific {
//   import ClockPlatformSpecific.Timer
//   import ClockPlatformSpecific.Timer
//   private[zio] val globalScheduler = new Scheduler {
//     import Scheduler.CancelToken

//     private[this] val ConstTrue  = () => false
//     private[this] val ConstFalse = () => false

//     override def schedule(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): CancelToken =
//       (duration: @unchecked) match {
//         case zio.Duration.Zero =>
//           task.run()
//           ConstTrue
//         case zio.Duration.Infinity => ConstFalse
//         case zio.Duration.Finite(nanos) =>
//           val future = ClockPlatformSpecific.scheduler.schedule(task, nanos, TimeUnit.NANOSECONDS)
//           () => future.cancel(true)
//       }
//   }
// }

// private object ClockPlatformSpecific {
//   // Multi-threaded scheduler using ScheduledExecutorService
//   val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

//   final class Timer private (private val scheduledFuture: java.util.concurrent.ScheduledFuture[_]) extends AnyVal {
//     def clear(): Unit =
//       scheduledFuture.cancel(false)
//   }

//   object Timer {
//     def delay(duration: FiniteDuration)(implicit trace: Trace): UIO[Unit] =
//       for {
//         promise <- Promise.make[Nothing, Unit]
//         _       <- ZIO.succeed(timeout(duration, () => promise.succeed(()).unit))
//         _       <- promise.await
//       } yield ()

//     private def timeout(duration: FiniteDuration, callback: () => Unit)(implicit trace: Trace): Timer = {
//       val scheduledFuture = scheduler.schedule(
//         new Runnable {
//           override def run(): Unit = callback()
//         },
//         duration.toMillis,
//         TimeUnit.MILLISECONDS
//       )
//       new Timer(scheduledFuture)
//     }

//     // // Legacy method without Trace to preserve binary compatibility
//     // def timeout(duration: FiniteDuration)(callback: () => Unit)(implicit unsafe: Unsafe): Timer = {
//     //   timeoutWithTrace(duration, callback)(Trace.empty)
//     // }

//     def repeat(duration: FiniteDuration)(callback: () => Unit)(implicit trace: Trace): Timer = {
//       val scheduledFuture = scheduler.scheduleAtFixedRate(
//         new Runnable {
//           override def run(): Unit = callback()
//         },
//         duration.toMillis,
//         duration.toMillis,
//         TimeUnit.MILLISECONDS
//       )
//       new Timer(scheduledFuture)
//     }
//   }
// }

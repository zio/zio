/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

package object profiling {
  private[profiling] final val SamplingNanos          = 1000000
  private[profiling] final val ExperimentMinTime      = 500000000L
  private[profiling] final val ExperimentTargetDelta  = 15
  private[profiling] final val ExperimentCoolOff: Int = SamplingNanos * 2
  private[profiling] final val ZeroSpeedupWeight      = 10
  private[profiling] final val MaxConsideredSpeedUp   = 100

  // tags that we need to execute delays before
  // anything that can wake a another fiber should be here
  private[profiling] val DelayImmediatelyOn: Set[Int] = {
    import ZIO.Tags._
    Set(
      FlatMap,
      Fold,
      Ensuring,
      Succeed,
      SucceedWith,
      Suspend,
      SuspendWith,
      Async,
      RaceWith
    )
  }

  private[profiling] val GlobalTrackerRef: FiberRef[Tracker] =
    Runtime.default.unsafeRun {
      FiberRef.make[Tracker](_ => ())
    }

  private[profiling] def selectSpeedUp(): Float =
    if (scala.util.Random.nextInt(ZeroSpeedupWeight) == 0) 0L
    else (scala.util.Random.nextInt(MaxConsideredSpeedUp) + 1).toFloat / 100
}

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

package zio.profiling

import com.github.ghik.silencer.silent
import zio.ZTraceElement

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

private final class Experiment(
  val candidate: ZTraceElement,
  val startTime: Long,
  val duration: Long,
  val speedUp: Float,
  private val progressPoints: ConcurrentHashMap[String, Int],
  delays0: Long,
  effectiveDuration0: Long
) {
  @volatile private[this] var delays: Long            = delays0
  @volatile private[this] var effectiveDuration: Long = effectiveDuration0

  val endTime: Long =
    startTime + duration

  def trackDelay(amount: Long): Unit = {
    delays += 1
    effectiveDuration -= amount
    ()
  }

  def inProgressAt(time: Long): Boolean =
    (time >= startTime && time < endTime)

  def addProgressPointMeasurement(name: String): Unit = {
    val now = java.lang.System.nanoTime()
    if (now >= startTime && now < endTime) {
      progressPoints.compute(name, (_, v) => v + 1)
      ()
    }
  }

  @silent("JavaConverters")
  def toResult(): ExperimentResult =
    ExperimentResult(
      candidate,
      speedUp,
      effectiveDuration,
      delays,
      progressPoints.asScala.map { case (name, delta) =>
        ThroughputData(
          name,
          delta
        )
      }.toList
    )
}

private object Experiment {

  def start(startTime: Long, duration: Long, candidate: ZTraceElement): Experiment = {
    val speedUp = selectSpeedUp()
    new Experiment(
      candidate,
      startTime,
      duration,
      speedUp,
      new ConcurrentHashMap(),
      0,
      duration
    )
  }

  @silent("JavaConverters")
  def fromPrevious(previous: Experiment, startTime: Long, candidate: ZTraceElement): Experiment = {
    val previousDeltas = previous.progressPoints.values().asScala

    val minDelta = if (previousDeltas.nonEmpty) previousDeltas.min else 0

    val nextDuration =
      if (minDelta < ExperimentTargetDelta) {
        previous.duration * 2
      } else if (minDelta >= ExperimentTargetDelta * 2 && previous.duration >= ExperimentMinTime * 2) {
        previous.duration / 2
      } else {
        previous.duration
      }

    Experiment.start(startTime + ExperimentCoolOff, nextDuration, candidate)
  }
}

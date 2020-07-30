/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

package object duration {

  val infiniteDuration = Duration.ofNanos(Long.MaxValue)
  val infiniteNano     = infiniteDuration.toNanos

  def fromNanos(nanos: Long): Duration     = if (nanos <= 0) Duration.ZERO else Duration.ofNanos(nanos)
  def fromNanos(nanos: Int): Duration      = if (nanos <= 0) Duration.ZERO else Duration.ofNanos(nanos.toLong)
  def fromMillis(millis: Long): Duration   = if (millis <= 0) Duration.ZERO else Duration.ofMillis(millis)
  def fromMillis(millis: Int): Duration    = if (millis <= 0) Duration.ZERO else Duration.ofMillis(millis.toLong)
  def fromSeconds(seconds: Long): Duration = if (seconds <= 0) Duration.ZERO else Duration.ofSeconds(seconds)
  def fromSeconds(seconds: Int): Duration  = if (seconds <= 0) Duration.ZERO else Duration.ofSeconds(seconds.toLong)
  def fromMinutes(minutes: Long): Duration = if (minutes <= 0) Duration.ZERO else Duration.ofMinutes(minutes)
  def fromMinutes(minutes: Int): Duration  = if (minutes <= 0) Duration.ZERO else Duration.ofMinutes(minutes.toLong)
  def fromHours(hours: Long): Duration     = if (hours <= 0) Duration.ZERO else Duration.ofHours(hours)


  def render(duration: Duration): String = {
    val nanos = duration.toNanos
    TimeUnit.NANOSECONDS.toMillis(nanos) match {
      case 0                       => s"$nanos ns"
      case millis if millis < 1000 => s"$millis ms"
      case millis if millis < 60000 =>
        val maybeMs = Option(millis % 1000).filterNot(_ == 0)
        s"${millis / 1000} s${maybeMs.fold("")(ms => s" $ms ms")}"
      case millis if millis < 3600000 =>
        val maybeSec = Option((millis % 60000) / 1000).filterNot(_ == 0)
        s"${millis / 60000} m${maybeSec.fold("")(s => s" $s s")}"
      case millis =>
        val days    = millis / 86400000
        val hours   = (millis % 86400000) / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000

        List(days, hours, minutes, seconds)
          .zip(List("d", "h", "m", "s"))
          .collect { case (value, unit) if value != 0 => s"$value $unit" }
          .mkString(" ")
    }
  }

  def multiply(duration: Duration, factor: Double): Duration = {
    val nanos = duration.toNanos
    if (factor <= 0 || nanos <= 0) Duration.ZERO
    else if (factor <= Long.MaxValue / nanos.toDouble) fromNanos((nanos * factor).round)
    else ChronoUnit.FOREVER.getDuration
  }

  def add(d1: Duration, d2: Duration): Duration = fromNanos(d1.toNanos + d2.toNanos)
  def add(d1: Duration, d2: Duration, d3: Duration): Duration =
    fromNanos(d1.toNanos + d2.toNanos + d3.toNanos)
  def add(d1: Duration, d2: Duration, d3: Duration, d4: Duration): Duration =
    fromNanos(d1.toNanos + d2.toNanos + d3.toNanos + d4.toNanos)


}

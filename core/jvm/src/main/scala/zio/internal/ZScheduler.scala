// File: core/jvm/src/main/scala/zio/internal/ZScheduler.scala

/*
 * Copyright 2018-2024 John A. De Goes and ZIO Contributors
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

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLongFieldUpdater}
import scala.annotation.switch
import zio.{Chunk, Trace, Unsafe, ZIO}
import zio.internal.stacktracer.ZTraceElement

private[zio] object ZScheduler {
  private val DefaultMaxWorkers = 64
  private val DefaultMinWorkers = 1

  private val DefaultKeepAliveTimeMs = 60000L // 60 seconds

  private def parseTimeUnit(unit: String): TimeUnit = {
    (unit.toLowerCase: @switch) match {
      case "ms" | "milli" | "millis" | "milliseconds" => TimeUnit.MILLISECONDS
      case "s" | "sec" | "secs" | "second" | "seconds"  => TimeUnit.SECONDS
      case "m" | "min" | "mins" | "minute" | "minutes"  => TimeUnit.MINUTES
      case _                                           => TimeUnit.MILLISECONDS
    }
  }

  private def parseKeepAliveTime(property: String): Long = {
    val trimmed = property.trim
    if (trimmed.isEmpty) {
      return DefaultKeepAliveTimeMs
    }

    val digitsEnd = trimmed.indexWhere(c => !c.isDigit)
    if (digitsEnd == 0) {
      return DefaultKeepAliveTimeMs
    }

    val (numStr, unitStr) =
      if (digitsEnd == -1) (trimmed, "ms")
      else (trimmed.substring(0, digitsEnd), trimmed.substring(digitsEnd))

    try {
      val value = numStr.toLong
      val unit  = parseTimeUnit(unitStr)
      Math.max(0L, unit.toMillis(value))
    } catch {
      case _: NumberFormatException => DefaultKeepAliveTimeMs
    }
  }

  private val keepAliveTime: Long = {
    val property = System.getProperty("zio.keeper.keep-alive-time", "").trim
    if (property.isEmpty) DefaultKeepAliveTimeMs
    else parseKeepAliveTime(property)
  }

  private val maxWorkers: Int = {
    val property = System.getProperty("zio.keeper.max-threads", "")
    if (property.isEmpty) {
      DefaultMaxWorkers
    } else {
      try {
        val value = property.trim.toInt
        if (value < DefaultMinWorkers) DefaultMinWorkers
        else if (value > 65536) 65536
        else value
      } catch {
        case _: NumberFormatException => DefaultMaxWorkers
      }
    }
  }

  private val minWorkers: Int = {
    val property = System.getProperty("zio.keeper.min-threads", "")
    if (property.isEmpty) {
      DefaultMinWorkers
    } else {
      try {
        val value = property.trim.toInt
        if (value < DefaultMinWorkers) DefaultMinWorkers
        else value
      } catch {
        case _: NumberFormatException => DefaultMinWorkers
      }
    }
  }

  private val workerCount: Int = {
    val property = System.getProperty("zio.keeper.workers", "")
    if (property.isEmpty) {
      maxWorkers
    } else {
      try {
        val value = property.trim.toInt
        if (value < minWorkers) minWorkers
        else if (value > maxWorkers) maxWorkers
        else value
      } catch {
        case _: NumberFormatException => maxWorkers
      }
    }
  }
}
/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test

import java.time.Duration
import java.time.{ Instant, LocalDateTime, OffsetDateTime, ZoneOffset }

import zio.duration._
import zio.random.Random

trait TimeVariants {

  /**
   * A generator of finite `java.time.Duration` values. Shrinks toward `Duration.ZERO`.
   */
  final def anyFiniteDuration: Gen[Random, Duration] = Gen.long(0L, Long.MaxValue).map(fromNanos)

  /**
   * A generator of `java.time.Instant` values. Shrinks toward `Instant.MIN`.
   */
  final def anyInstant: Gen[Random, Instant] = instant(Instant.MIN, Instant.MAX)

  /**
   * A generator of `java.time.LocalDateTime` values. Shrinks toward `LocalDateTime.MIN`.
   */
  final def anyLocalDateTime: Gen[Random, LocalDateTime] = localDateTime(LocalDateTime.MIN, LocalDateTime.MAX)

  /**
   * A generator of `java.time.OffsetDateTime` values. Shrinks toward `OffsetDateTime.MIN`.
   */
  final def anyOffsetDateTime: Gen[Random, OffsetDateTime] = offsetDateTime(OffsetDateTime.MIN, OffsetDateTime.MAX)

  /**
   * A generator of finite `zio.duration.Duration` values inside the specified range: [min, max]. Shrinks toward min.
   */
  final def finiteDuration(min: Duration, max: Duration): Gen[Random, Duration] =
    Gen.long(min.toNanos, max.toNanos).map(fromNanos)

  /**
   * A generator of `java.time.Instant` values inside the specified range: [min, max]. Shrinks toward min.
   */
  final def instant(min: Instant, max: Instant): Gen[Random, Instant] = {

    def genSecond(min: Instant, max: Instant): Gen[Random, Long] = Gen.long(min.getEpochSecond, max.getEpochSecond - 1)

    def genNano(min: Instant, max: Instant, second: Long): Gen[Random, Long] = {
      val minNano = if (min.getEpochSecond == second) min.getNano.toLong else 0L
      val maxNano = if (max.getEpochSecond == second) max.getNano.toLong else 1000000000L
      Gen.long(minNano, maxNano)
    }

    for {
      second       <- genSecond(min, max)
      nanoFraction <- genNano(min, max, second)
    } yield Instant.ofEpochSecond(second, nanoFraction)
  }

  /**
   * A generator of `java.time.LocalDateTime` values inside the specified range: [min, max]. Shrinks toward min.
   */
  final def localDateTime(min: LocalDateTime, max: LocalDateTime): Gen[Random, LocalDateTime] =
    instant(min.toInstant(utc), max.toInstant(utc)).map(LocalDateTime.ofInstant(_, utc))

  /**
   * A generator of `java.time.OffsetDateTime` values inside the specified range: [min, max]. Shrinks toward min.
   */
  final def offsetDateTime(min: OffsetDateTime, max: OffsetDateTime): Gen[Random, OffsetDateTime] = {

    def genLocalDateTime(min: OffsetDateTime, max: OffsetDateTime): Gen[Random, LocalDateTime] = {
      val minInst = min.atZoneSimilarLocal(utc).toInstant
      val maxInst = max.atZoneSimilarLocal(utc).toInstant
      instant(minInst, maxInst).map(_.atOffset(utc).toLocalDateTime)
    }

    def genOffset(min: OffsetDateTime, max: OffsetDateTime, actual: LocalDateTime): Gen[Random, ZoneOffset] = {
      val minLocalDate     = min.atZoneSimilarLocal(utc).toLocalDate
      val maxLocalDate     = max.atZoneSimilarLocal(utc).toLocalDate
      val actualLocalDate  = actual.toLocalDate
      val minOffsetSeconds = if (minLocalDate == actualLocalDate) min.getOffset.getTotalSeconds else -18 * 3600
      val maxOffsetSeconds = if (maxLocalDate == actualLocalDate) max.getOffset.getTotalSeconds else 18 * 3600
      Gen.int(minOffsetSeconds, maxOffsetSeconds).map(ZoneOffset.ofTotalSeconds(_))
    }

    for {
      localDateTime <- genLocalDateTime(min, max)
      offset        <- genOffset(min, max, localDateTime)
    } yield OffsetDateTime.of(localDateTime, offset)
  }

  private val utc: ZoneOffset = ZoneOffset.UTC
}

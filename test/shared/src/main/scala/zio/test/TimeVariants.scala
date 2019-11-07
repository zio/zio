/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import java.time.{ Instant, LocalDateTime, OffsetDateTime, ZoneOffset }

import zio.duration.Duration
import zio.random.Random

trait TimeVariants {

  /**
   * A generator of ZIO duration values. Shrinks toward Duration.Zero.
   */
  final def anyFiniteDuration: Gen[Random, Duration] = Gen.long(0L, Long.MaxValue).map(Duration.Finite(_))

  /**
   * A generator java.time.Instant values.
   */
  final def anyInstant: Gen[Random, Instant] = instant(Instant.MIN, Instant.MAX)

  /**
   * A generator java.time.LocalDateTime values.
   */
  final def anyLocalDateTime: Gen[Random, LocalDateTime] = localDateTime(LocalDateTime.MIN, LocalDateTime.MAX)

  /**
   * A generator java.time.OffsetDateTime values.
   */
  final def anyOffsetDateTime: Gen[Random, OffsetDateTime] = offsetDateTime(OffsetDateTime.MIN, OffsetDateTime.MAX)

  /**
   * A generator of ZIO duration values inside the specified range: [min, max].
   */
  final def duration(min: Duration, max: Duration): Gen[Random, Duration] =
    Gen.long(min.toNanos, max.toNanos).map(Duration.Finite(_))

  /**
   * A generator of java.time.Instant values inside the specified range: [min, max].
   */
  final def instant(min: Instant, max: Instant): Gen[Random, Instant] =
    for {
      second       <- Gen.long(min.getEpochSecond, max.getEpochSecond - 1)
      nanoFraction <- Gen.long(0L, 1000000000L)
    } yield Instant.ofEpochSecond(second, nanoFraction)

  /**
   * A generator of java.time.LocalDateTime values inside the specified range: [min, max].
   */
  final def localDateTime(min: LocalDateTime, max: LocalDateTime): Gen[Random, LocalDateTime] =
    instant(min.toInstant(utc), max.toInstant(utc)).map(LocalDateTime.ofInstant(_, utc))

  /**
   * A generator of java.time.OffsetDateTime values inside the specified range: [min, max].
   */
  final def offsetDateTime(min: OffsetDateTime, max: OffsetDateTime): Gen[Random, OffsetDateTime] = {
    val minInst: Instant = min.plusSeconds(min.getOffset.getTotalSeconds.toLong).toInstant
    val maxInst: Instant = max.plusSeconds(max.getOffset.getTotalSeconds.toLong).toInstant
    Gen
      .int(-18, 18)
      .map(offsetHours => ZoneOffset.ofHours(offsetHours))
      .flatMap { offset =>
        instant(minInst, maxInst).map(inst => OffsetDateTime.ofInstant(inst, offset))
      }
  }

  private val utc: ZoneOffset = ZoneOffset.UTC
}

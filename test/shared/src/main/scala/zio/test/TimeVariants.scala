/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.duration.Duration
import zio.random.Random

import java.time._
import scala.jdk.CollectionConverters._

trait TimeVariants {

  /**
   * A generator of `java.time.DayOfWeek` values. Doesn't have any shrinking.
   */
  final def anyDayOfWeek: Gen[Random, DayOfWeek] =
    Gen.oneOf(
      Gen.const(DayOfWeek.MONDAY),
      Gen.const(DayOfWeek.TUESDAY),
      Gen.const(DayOfWeek.WEDNESDAY),
      Gen.const(DayOfWeek.THURSDAY),
      Gen.const(DayOfWeek.FRIDAY),
      Gen.const(DayOfWeek.SATURDAY),
      Gen.const(DayOfWeek.SUNDAY)
    )

  /**
   * A generator of `java.time.Duration` values. Shrinks toward `Duration.of(Long.MinValue, 0)`
   */
  final def anyDuration: Gen[Random, Duration] =
    for {
      seconds <- Gen.anyLong
      nanos   <- Gen.long(0, 999999999L)
    } yield java.time.Duration.ofSeconds(seconds, nanos)

  /**
   * A generator of finite `zio.duration.Duration` values. Shrinks toward `Duration.Zero`.
   */
  final def anyFiniteDuration: Gen[Random, Duration] = Gen.long(0L, Long.MaxValue).map(Duration.Finite(_))

  /**
   * A generator of `java.time.Instant` values. Shrinks toward `Instant.MIN`.
   */
  final def anyInstant: Gen[Random, Instant] = instant(Instant.MIN, Instant.MAX)

  /**
   * A generator of `java.time.LocalDate` values. Shrinks toward `LocaDate.MIN`
   */
  final def anyLocalDate: Gen[Random, LocalDate] =
    for {
      year  <- anyYear
      month <- Gen.int(1, 12)
      day   <- Gen.int(1, Month.of(month).maxLength())
    } yield LocalDate.of(year.getValue, month, day)

  /**
   * A generator of `java.time.LocalTime` values. Shrinks toward `LocalTime.MIN`
   */
  final def anyLocalTime: Gen[Random, LocalTime] =
    for {
      hour   <- Gen.int(0, 23)
      minute <- Gen.int(0, 59)
      second <- Gen.int(0, 59)
      nanos  <- Gen.int(0, 999_999_999)
    } yield LocalTime.of(hour, minute, second, nanos)

  /**
   * A generator of `java.time.LocalDateTime` values. Shrinks toward `LocalDateTime.MIN`.
   */
  final def anyLocalDateTime: Gen[Random, LocalDateTime] = localDateTime(LocalDateTime.MIN, LocalDateTime.MAX)

  /**
   * A generator of `java.time.Month` values. Doesn't have any shrinking.
   */
  final def anyMonth: Gen[Random, Month] =
    Gen.oneOf(
      Gen.const(Month.JANUARY),
      Gen.const(Month.FEBRUARY),
      Gen.const(Month.MARCH),
      Gen.const(Month.APRIL),
      Gen.const(Month.MAY),
      Gen.const(Month.JUNE),
      Gen.const(Month.JULY),
      Gen.const(Month.AUGUST),
      Gen.const(Month.SEPTEMBER),
      Gen.const(Month.OCTOBER),
      Gen.const(Month.NOVEMBER),
      Gen.const(Month.DECEMBER)
    )

  /**
   * A generator of `java.time.MonthDay` values. Shrinks toward `MonthDay.of(Month.JANUARY, 1)`
   */
  final def anyMonthDay: Gen[Random, MonthDay] =
    for {
      month <- Gen.int(1, 12).map(Month.of)
      days  <- Gen.int(1, month.maxLength())
    } yield MonthDay.of(month, days)

  /**
   * A generator of `java.time.OffsetDateTime` values. Shrinks toward `OffsetDateTime.MIN`.
   */
  final def anyOffsetDateTime: Gen[Random, OffsetDateTime] = offsetDateTime(OffsetDateTime.MIN, OffsetDateTime.MAX)

  /**
   * A generator of `java.time.OffsetTime` values. Shrinks torward `OffsetTime.MIN`
   */
  final def anyOffsetTime: Gen[Random, OffsetTime] =
    for {
      time   <- anyLocalTime
      offset <- anyZoneOffset
    } yield OffsetTime.of(time, offset)

  /**
   * A generator of `java.time.Period` values. Shrinks toward `Period.of(0, 0, 0)`
   */
  final def anyPeriod: Gen[Random, Period] =
    for {
      years  <- Gen.int(0, Int.MaxValue)
      months <- Gen.int(0, Int.MaxValue)
      days   <- Gen.int(0, Int.MaxValue)
    } yield Period.of(years, months, days)

  /**
   * A generator of `java.time.Year` values. Shrinks toward `Year.MIN_VALUE`.
   */
  final def anyYear: Gen[Random, Year] =
    Gen.int(Year.MIN_VALUE, Year.MAX_VALUE).map(Year.of)

  /**
   * A generator of `java.time.YearMonth` values. Shrinks toward `YearMonth.of(Year.MIN_VALUE, Month.JANUARY)`
   */
  final def anyYearMonth: Gen[Random, YearMonth] =
    for {
      year  <- anyYear
      month <- Gen.int(1, 12)
    } yield YearMonth.of(year.getValue(), month)

  /**
   * A generator of `java.time.ZonedDateTime` values. Shrinks toward `ZoneDateTime.of(LocalDateTime.MIN, zoneId)`
   */
  final def anyZonedDateTime: Gen[Random, ZonedDateTime] =
    for {
      dateTime <- anyLocalDateTime
      zoneId   <- anyZoneId
    } yield ZonedDateTime.of(dateTime, zoneId)

  /**
   * A generator of `java.time.ZoneId` values. Doesn't have any shrinking.
   */
  final def anyZoneId: Gen[Random, ZoneId] = {
    val zoneIds = ZoneId.getAvailableZoneIds().asScala
    val idMap   = Set.range(0, zoneIds.size).zip(zoneIds).toMap

    Gen.int(0, zoneIds.size).noShrink.map(idx => ZoneId.of(idMap(idx)))
  }

  /**
   * A generator of `java.time.ZoneOffset` values. Shrinks toward `ZoneOffset.MIN`
   */
  final def anyZoneOffset: Gen[Random, ZoneOffset] =
    Gen
      .int(ZoneOffset.MIN.getTotalSeconds, ZoneOffset.MAX.getTotalSeconds)
      .map(ZoneOffset.ofTotalSeconds)

  /**
   * A generator of finite `zio.duration.Duration` values inside the specified range: [min, max]. Shrinks toward min.
   */
  final def finiteDuration(min: Duration, max: Duration): Gen[Random, Duration] =
    Gen.long(min.toNanos, max.toNanos).map(Duration.Finite(_))

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

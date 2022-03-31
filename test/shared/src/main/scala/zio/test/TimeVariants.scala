/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import com.github.ghik.silencer.silent
import zio.{Duration, Random, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time._
import scala.collection.JavaConverters._

trait TimeVariants {

  /**
   * A generator of `java.time.DayOfWeek` values. Shrinks toward
   * `DayOfWeek.MONDAY`.
   */
  @deprecated("use dayOfWeek", "2.0.0")
  final def anyDayOfWeek(implicit trace: ZTraceElement): Gen[Any, DayOfWeek] =
    Gen.dayOfWeek

  /**
   * A generator of finite `zio.duration.Duration` values. Shrinks toward
   * `Duration.Zero`.
   */
  @deprecated("use finiteDuration", "2.0.0")
  final def anyFiniteDuration(implicit trace: ZTraceElement): Gen[Any, Duration] =
    Gen.finiteDuration

  /**
   * A generator of `java.time.Instant` values. Shrinks toward `Instant.MIN`.
   */
  @deprecated("use instant", "2.0.0")
  final def anyInstant(implicit trace: ZTraceElement): Gen[Any, Instant] =
    Gen.instant

  /**
   * A generator of `java.time.LocalDate` values. Shrinks toward
   * `LocalDate.MIN`.
   */
  @deprecated("use localDate", "2.0.0")
  final def anyLocalDate(implicit trace: ZTraceElement): Gen[Any, LocalDate] =
    Gen.localDate

  /**
   * A generator of `java.time.LocalTime` values. Shrinks toward
   * `LocalTime.MIN`.
   */
  @deprecated("use localTime", "2.0.0")
  final def anyLocalTime(implicit trace: ZTraceElement): Gen[Any, LocalTime] =
    Gen.localTime

  /**
   * A generator of `java.time.LocalDateTime` values. Shrinks toward
   * `LocalDateTime.MIN`.
   */
  @deprecated("use localDateTime", "2.0.0")
  final def anyLocalDateTime(implicit trace: ZTraceElement): Gen[Any, LocalDateTime] =
    Gen.localDateTime

  /**
   * A generator of `java.time.Month` values. Shrinks toward `Month.JANUARY`.
   */
  @deprecated("use month", "2.0.0")
  final def anyMonth(implicit trace: ZTraceElement): Gen[Any, Month] =
    Gen.month

  /**
   * A generator of `java.time.MonthDay` values. Shrinks toward
   * `MonthDay.of(Month.JANUARY, 1)`.
   */
  @deprecated("use monthDay", "2.0.0")
  final def anyMonthDay(implicit trace: ZTraceElement): Gen[Any, MonthDay] =
    Gen.monthDay

  /**
   * A generator of `java.time.OffsetDateTime` values. Shrinks toward
   * `OffsetDateTime.MIN`.
   */
  @deprecated("use offsetDateTime", "2.0.0")
  final def anyOffsetDateTime(implicit trace: ZTraceElement): Gen[Any, OffsetDateTime] =
    Gen.offsetDateTime

  /**
   * A generator of `java.time.OffsetTime` values. Shrinks torward
   * `OffsetTime.MIN`.
   */
  @deprecated("use offsetTime", "2.0.0")
  final def anyOffsetTime(implicit trace: ZTraceElement): Gen[Any, OffsetTime] =
    Gen.offsetTime

  /**
   * A generator of `java.time.Period` values. Shrinks toward `Period.ZERO`.
   */
  @deprecated("use period", "2.0.0")
  final def anyPeriod(implicit trace: ZTraceElement): Gen[Any, Period] =
    Gen.period

  /**
   * A generator of `java.time.Year` values. Shrinks toward
   * `Year.of(Year.MIN_VALUE)`.
   */
  @deprecated("use year", "2.0.0")
  final def anyYear(implicit trace: ZTraceElement): Gen[Any, Year] =
    Gen.year

  /**
   * A generator of `java.time.YearMonth` values. Shrinks toward
   * `YearMonth.of(Year.MIN_VALUE, Month.JANUARY)`.
   */
  @deprecated("use yearMonth", "2.0.0")
  final def anyYearMonth(implicit trace: ZTraceElement): Gen[Any, YearMonth] =
    Gen.yearMonth

  /**
   * A generator of `java.time.ZonedDateTime` values. Shrinks toward
   * `ZoneDateTime.of(LocalDateTime.MIN, zoneId)`.
   */
  @deprecated("use zonedDateTime", "2.0.0")
  final def anyZonedDateTime(implicit trace: ZTraceElement): Gen[Any, ZonedDateTime] =
    Gen.zonedDateTime

  /**
   * A generator of `java.time.ZoneId` values. Doesn't have any shrinking.
   */
  @deprecated("use zoneId", "2.0.0")
  final def anyZoneId(implicit trace: ZTraceElement): Gen[Any, ZoneId] =
    Gen.zoneId

  /**
   * A generator of `java.time.ZoneOffset` values. Shrinks toward
   * `ZoneOffset.MIN`.
   */
  @deprecated("use zoneOffset", "2.0.0")
  final def anyZoneOffset(implicit trace: ZTraceElement): Gen[Any, ZoneOffset] =
    Gen.zoneOffset

  /**
   * A generator of `java.time.DayOfWeek` values. Shrinks toward
   * `DayOfWeek.MONDAY`.
   */
  final def dayOfWeek(implicit trace: ZTraceElement): Gen[Any, DayOfWeek] =
    Gen.elements(
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
      DayOfWeek.SATURDAY,
      DayOfWeek.SUNDAY
    )

  /**
   * A generator of finite `zio.duration.Duration` values. Shrinks toward
   * `Duration.Zero`.
   */
  final def finiteDuration(implicit trace: ZTraceElement): Gen[Any, Duration] =
    Gen.long(0L, Long.MaxValue).map(Duration.Finite(_))

  /**
   * A generator of finite `zio.duration.Duration` values inside the specified
   * range: [min, max]. Shrinks toward min.
   */
  final def finiteDuration(min: Duration, max: Duration)(implicit trace: ZTraceElement): Gen[Any, Duration] =
    Gen.long(min.toNanos, max.toNanos).map(Duration.Finite(_))

  /**
   * A generator of `java.time.Instant` values. Shrinks toward `Instant.MIN`.
   */
  final def instant(implicit trace: ZTraceElement): Gen[Any, Instant] =
    instant(Instant.MIN, Instant.MAX)

  /**
   * A generator of `java.time.Instant` values inside the specified range: [min,
   * max]. Shrinks toward min.
   */
  final def instant(min: Instant, max: Instant)(implicit trace: ZTraceElement): Gen[Any, Instant] = {

    def genSecond(min: Instant, max: Instant): Gen[Any, Long] =
      Gen.long(min.getEpochSecond, max.getEpochSecond - 1)

    def genNano(min: Instant, max: Instant, second: Long): Gen[Any, Long] = {
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
   * A generator of `java.time.LocalDate` values. Shrinks toward
   * `LocalDate.MIN`.
   */
  final def localDate(implicit trace: ZTraceElement): Gen[Any, LocalDate] =
    for {
      year  <- year
      month <- Gen.int(1, 12)
      maxLen = if (!year.isLeap && month == 2) 28 else Month.of(month).maxLength
      day   <- Gen.int(1, maxLen)
    } yield LocalDate.of(year.getValue, month, day)

  /**
   * A generator of `java.time.LocalDateTime` values. Shrinks toward
   * `LocalDateTime.MIN`.
   */
  final def localDateTime(implicit trace: ZTraceElement): Gen[Any, LocalDateTime] =
    localDateTime(LocalDateTime.MIN, LocalDateTime.MAX)

  /**
   * A generator of `java.time.LocalDateTime` values inside the specified range:
   * [min, max]. Shrinks toward min.
   */
  final def localDateTime(min: LocalDateTime, max: LocalDateTime)(implicit
    trace: ZTraceElement
  ): Gen[Any, LocalDateTime] =
    instant(min.toInstant(utc), max.toInstant(utc)).map(LocalDateTime.ofInstant(_, utc))

  /**
   * A generator of `java.time.LocalTime` values. Shrinks toward
   * `LocalTime.MIN`.
   */
  final def localTime(implicit trace: ZTraceElement): Gen[Any, LocalTime] =
    for {
      hour   <- Gen.int(0, 23)
      minute <- Gen.int(0, 59)
      second <- Gen.int(0, 59)
      nanos  <- Gen.int(0, 999999999)
    } yield LocalTime.of(hour, minute, second, nanos)

  /**
   * A generator of `java.time.Month` values. Shrinks toward `Month.JANUARY`.
   */
  final def month(implicit trace: ZTraceElement): Gen[Any, Month] =
    Gen.elements(
      Month.JANUARY,
      Month.FEBRUARY,
      Month.MARCH,
      Month.APRIL,
      Month.MAY,
      Month.JUNE,
      Month.JULY,
      Month.AUGUST,
      Month.SEPTEMBER,
      Month.OCTOBER,
      Month.NOVEMBER,
      Month.DECEMBER
    )

  /**
   * A generator of `java.time.MonthDay` values. Shrinks toward
   * `MonthDay.of(Month.JANUARY, 1)`.
   */
  final def monthDay(implicit trace: ZTraceElement): Gen[Any, MonthDay] =
    for {
      month <- Gen.int(1, 12).map(Month.of)
      days  <- Gen.int(1, month.maxLength())
    } yield MonthDay.of(month, days)

  /**
   * A generator of `java.time.OffsetDateTime` values. Shrinks toward
   * `OffsetDateTime.MIN`.
   */
  final def offsetDateTime(implicit trace: ZTraceElement): Gen[Any, OffsetDateTime] =
    offsetDateTime(OffsetDateTime.MIN, OffsetDateTime.MAX)

  /**
   * A generator of `java.time.OffsetDateTime` values inside the specified
   * range: [min, max]. Shrinks toward min.
   */
  final def offsetDateTime(min: OffsetDateTime, max: OffsetDateTime)(implicit
    trace: ZTraceElement
  ): Gen[Any, OffsetDateTime] = {

    def genLocalDateTime(min: OffsetDateTime, max: OffsetDateTime): Gen[Any, LocalDateTime] = {
      val minInst = min.atZoneSimilarLocal(utc).toInstant
      val maxInst = max.atZoneSimilarLocal(utc).toInstant
      instant(minInst, maxInst).map(_.atOffset(utc).toLocalDateTime)
    }

    def genOffset(min: OffsetDateTime, max: OffsetDateTime, actual: LocalDateTime): Gen[Any, ZoneOffset] = {
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

  /**
   * A generator of `java.time.OffsetTime` values. Shrinks torward
   * `OffsetTime.MIN`.
   */
  final def offsetTime(implicit trace: ZTraceElement): Gen[Any, OffsetTime] =
    for {
      time   <- localTime
      offset <- zoneOffset
    } yield OffsetTime.of(time, ZoneOffset.ofTotalSeconds(-offset.getTotalSeconds))

  /**
   * A generator of `java.time.Period` values. Shrinks toward `Period.ZERO`.
   */
  final def period(implicit trace: ZTraceElement): Gen[Any, Period] =
    for {
      years  <- Gen.int(0, Int.MaxValue)
      months <- Gen.int(0, Int.MaxValue)
      days   <- Gen.int(0, Int.MaxValue)
    } yield Period.of(years, months, days)

  /**
   * A generator of `java.time.Year` values. Shrinks toward
   * `Year.of(Year.MIN_VALUE)`.
   */
  final def year(implicit trace: ZTraceElement): Gen[Any, Year] =
    Gen.int(Year.MIN_VALUE, Year.MAX_VALUE).map(Year.of)

  /**
   * A generator of `java.time.YearMonth` values. Shrinks toward
   * `YearMonth.of(Year.MIN_VALUE, Month.JANUARY)`.
   */
  final def yearMonth(implicit trace: ZTraceElement): Gen[Any, YearMonth] =
    for {
      year  <- year
      month <- Gen.int(1, 12)
    } yield YearMonth.of(year.getValue(), month)

  /**
   * A generator of `java.time.ZonedDateTime` values. Shrinks toward
   * `ZoneDateTime.of(LocalDateTime.MIN, zoneId)`.
   */
  final def zonedDateTime(implicit trace: ZTraceElement): Gen[Any, ZonedDateTime] =
    for {
      dateTime <- localDateTime
      zoneId   <- zoneId
    } yield ZonedDateTime.of(dateTime, zoneId)

  /**
   * A generator of `java.time.ZoneId` values. Doesn't have any shrinking.
   */
  @silent("JavaConverters")
  final def zoneId(implicit trace: ZTraceElement): Gen[Any, ZoneId] =
    Gen.elements(ZoneId.getAvailableZoneIds.asScala.map(ZoneId.of).toList: _*).noShrink

  /**
   * A generator of `java.time.ZoneOffset` values. Shrinks toward
   * `ZoneOffset.MIN`.
   */
  final def zoneOffset(implicit trace: ZTraceElement): Gen[Any, ZoneOffset] =
    Gen
      .int(ZoneOffset.MIN.getTotalSeconds, ZoneOffset.MAX.getTotalSeconds)
      .map(ZoneOffset.ofTotalSeconds)

  private val utc: ZoneOffset = ZoneOffset.UTC
}

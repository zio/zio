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

import scala.concurrent.Future

import zio.duration._
import zio.test.GenUtils.{ checkSample, checkShrink }
import zio.test.TestUtils.label

object TimeVariantsSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(anyFiniteDurationGeneratesDurations, "anyFiniteDuration generates Duration values"),
    label(anyFiniteDurationShrinksToZero, "anyFiniteDuration shrinks to Duration.Zero"),
    label(anyInstantGeneratesInstants, "anyInstant generates Instant values"),
    label(anyInstantShrinksToMin, "anyInstant shrinks to Instant.MIN"),
    label(anyLocalDateTimeGeneratesLocalDateTimes, "anyLocalDateTime generates LocalDateTime values"),
    label(anyLocalDateTimeShrinksToMin, "anyLocalDateTime shrinks to LocalDateTime.MIN"),
    label(anyOffsetDateTimeGeneratesOffsetDateTimes, "anyOffsetDateTime generates OffsetDateTime values"),
    label(anyOffsetDateTimeShrinksToMin, "anyOffsetDateTime shrinks to OffsetDateTime.MIN"),
    label(finiteDurationGeneratesValuesInRange, "finiteDuration generates values in range"),
    label(finiteDurationShrinksToMin, "finiteDuration shrinks to min"),
    label(instantGeneratesValuesInRange, "instant generates values in range"),
    label(instantShrinksToMin, "instant shrinks to min"),
    label(localDateTimeGeneratesValuesInRange, "localDateTime generates values in range"),
    label(localDateTimeShrinksToMin, "localDateTime shrinks to min"),
    label(offsetDateTimeGeneratesValuesInRange, "offsetDateTime generates values in range"),
    label(offsetDateTimeShrinksToMin, "offsetDateTime shrinks to min")
  )

  def anyFiniteDurationGeneratesDurations: Future[Boolean] = checkSample(Gen.anyFiniteDuration)(_.nonEmpty)

  def anyFiniteDurationShrinksToZero: Future[Boolean] = checkShrink(Gen.anyFiniteDuration)(Duration.Zero)

  def anyInstantGeneratesInstants: Future[Boolean] = checkSample(Gen.anyInstant)(_.nonEmpty)

  def anyInstantShrinksToMin: Future[Boolean] = checkShrink(Gen.anyInstant)(Instant.MIN)

  def anyLocalDateTimeGeneratesLocalDateTimes: Future[Boolean] = checkSample(Gen.anyLocalDateTime)(_.nonEmpty)

  def anyLocalDateTimeShrinksToMin: Future[Boolean] = checkShrink(Gen.anyLocalDateTime)(LocalDateTime.MIN)

  def anyOffsetDateTimeGeneratesOffsetDateTimes: Future[Boolean] = checkSample(Gen.anyOffsetDateTime)(_.nonEmpty)

  def anyOffsetDateTimeShrinksToMin: Future[Boolean] = checkShrink(Gen.anyOffsetDateTime)(OffsetDateTime.MIN)

  def finiteDurationGeneratesValuesInRange: Future[Boolean] = {
    val min = 42.minutes + 23222.nanos
    val max = 3.hours + 30.seconds + 887999.nanos
    checkSample(Gen.finiteDuration(min, max))(_.forall(n => min <= n && n <= max))
  }

  def finiteDurationShrinksToMin: Future[Boolean] = {
    val min = 97.minutes + 13.seconds + 32.nanos
    val max = 3.hours + 2.minutes + 45.seconds + 23453.nanos
    checkShrink(Gen.finiteDuration(min, max))(min)
  }

  def instantGeneratesValuesInRange: Future[Boolean] = {
    val min = Instant.ofEpochSecond(-38457693893669L, 435345)
    val max = Instant.ofEpochSecond(74576982873324L, 345345345)
    checkSample(Gen.instant(min, max))(_.forall(n => !n.isBefore(min) && !n.isAfter(max)))
  }

  def instantShrinksToMin: Future[Boolean] = {
    val min = Instant.ofEpochSecond(-93487534873L, 2387642L)
    val max = Instant.ofEpochSecond(394876L, 376542888L)
    checkShrink(Gen.instant(min, max))(min)
  }

  def localDateTimeGeneratesValuesInRange: Future[Boolean] = {
    val min = LocalDateTime.ofEpochSecond(-238756L, 987435, ZoneOffset.ofHours(12))
    val max = LocalDateTime.ofEpochSecond(3987384759834L, 4736, ZoneOffset.ofHours(-2))
    checkSample(Gen.localDateTime(min, max))(_.forall(n => !n.isBefore(min) && !n.isAfter(max)))
  }

  def localDateTimeShrinksToMin: Future[Boolean] = {
    val min = LocalDateTime.ofEpochSecond(-349875349L, 38743843, ZoneOffset.ofHours(-13))
    val max = LocalDateTime.ofEpochSecond(-234234L, 34985434, ZoneOffset.ofHours(-1))
    checkShrink(Gen.localDateTime(min, max))(min)
  }

  def offsetDateTimeGeneratesValuesInRange: Future[Boolean] = {
    val min = OffsetDateTime.ofInstant(Instant.ofEpochSecond(-98345983298736L, 34334), ZoneOffset.ofHours(7))
    val max = OffsetDateTime.ofInstant(Instant.ofEpochSecond(39847530948982L, 4875384), ZoneOffset.ofHours(3))
    checkSample(Gen.offsetDateTime(min, max))(_.forall(n => !n.isBefore(min) && !n.isAfter(max)))
  }

  def offsetDateTimeShrinksToMin: Future[Boolean] = {
    val min = OffsetDateTime.ofInstant(Instant.ofEpochSecond(8345983298736L, 345), ZoneOffset.ofHours(-4))
    val max = OffsetDateTime.ofInstant(Instant.ofEpochSecond(348975394875348L, 56456456), ZoneOffset.ofHours(0))
    checkShrink(Gen.offsetDateTime(min, max))(min)
  }

}

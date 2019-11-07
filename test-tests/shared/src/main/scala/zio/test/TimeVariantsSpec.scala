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
    label(anyDurationShrinksToZero, "anyDuration shrinks to zero"),
    label(anyInstantGeneratesInstants, "anyInstant generates Instant values"),
    label(anyLocalDateTimeGeneratesLocalDateTimes, "anyLocalDateTime generates LocalDateTime values"),
    label(anyOffsetDateTimeGeneratesOffsetDateTimes, "anyOffsetDateTime generates OffsetDateTime values"),
    label(durationGeneratesValuesInRange, "duration generates values in range"),
    label(instantGeneratesValuesInRange, "instant generates values in range"),
    label(localDateTimeGeneratesValuesInRange, "localDateTime generates values in range"),
    label(offsetDateTimeGeneratesValuesInRange, "offsetDateTime generates values in range")
  )

  def anyDurationShrinksToZero: Future[Boolean] = checkShrink(Gen.anyDuration)(Duration.Zero)

  def anyInstantGeneratesInstants: Future[Boolean] = checkSample(Gen.anyInstant)(_.nonEmpty)

  def anyLocalDateTimeGeneratesLocalDateTimes: Future[Boolean] = checkSample(Gen.anyLocalDateTime)(_.nonEmpty)

  def anyOffsetDateTimeGeneratesOffsetDateTimes: Future[Boolean] = checkSample(Gen.anyOffsetDateTime)(_.nonEmpty)

  def durationGeneratesValuesInRange: Future[Boolean] = {
    val min = 42.minutes
    val max = 3.hours
    checkSample(Gen.duration(min, max))(_.forall(n => min <= n && n <= max))
  }

  def instantGeneratesValuesInRange: Future[Boolean] = {
    val min = Instant.ofEpochSecond(-38457693893669L)
    val max = Instant.ofEpochSecond(74576982873324L)
    checkSample(Gen.instant(min, max))(_.forall(n => !n.isBefore(min) && !n.isAfter(max)))
  }

  def localDateTimeGeneratesValuesInRange: Future[Boolean] = {
    val min = LocalDateTime.ofEpochSecond(-238756L, 987435, ZoneOffset.ofHours(12))
    val max = LocalDateTime.ofEpochSecond(3987384759834L, 4736, ZoneOffset.ofHours(-2))
    checkSample(Gen.localDateTime(min, max))(_.forall(n => !n.isBefore(min) && !n.isAfter(max)))
  }

  def offsetDateTimeGeneratesValuesInRange: Future[Boolean] = {
    val min = OffsetDateTime.ofInstant(Instant.ofEpochSecond(-98345983298736L), ZoneOffset.ofHours(7))
    val max = OffsetDateTime.ofInstant(Instant.ofEpochSecond(39847530948982L), ZoneOffset.ofHours(3))
    checkSample(Gen.offsetDateTime(min, max))(_.forall(n => !n.isBefore(min) && !n.isAfter(max)))
  }

}

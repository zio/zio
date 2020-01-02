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

package zio.duration

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._

final class DurationSyntax(n: Long) {

  private[this] def asDuration(unit: TimeUnit): Duration = Duration(n, unit)

  def nanoseconds = asDuration(NANOSECONDS)
  def nanos       = nanoseconds
  def nanosecond  = nanoseconds
  def nano        = nanoseconds

  def microseconds = asDuration(MICROSECONDS)
  def micros       = microseconds
  def microsecond  = microseconds
  def micro        = microseconds

  def milliseconds = asDuration(MILLISECONDS)
  def millis       = milliseconds
  def millisecond  = milliseconds
  def milli        = milliseconds

  def seconds = asDuration(SECONDS)
  def second  = seconds

  def minutes = asDuration(MINUTES)
  def minute  = minutes

  def hours = asDuration(HOURS)
  def hour  = hours

  def days = asDuration(DAYS)
  def day  = days

}

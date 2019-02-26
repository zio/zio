/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.duration

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._

class DurationSyntax(n: Long) {

  protected def asDuration(unit: TimeUnit): Duration = Duration(n, unit)

  final def nanoseconds = asDuration(NANOSECONDS)
  final def nanos       = nanoseconds
  final def nanosecond  = nanoseconds
  final def nano        = nanoseconds

  final def microseconds = asDuration(MICROSECONDS)
  final def micros       = microseconds
  final def microsecond  = microseconds
  final def micro        = microseconds

  final def milliseconds = asDuration(MILLISECONDS)
  final def millis       = milliseconds
  final def millisecond  = milliseconds
  final def milli        = milliseconds

  final def seconds = asDuration(SECONDS)
  final def second  = seconds

  final def minutes = asDuration(MINUTES)
  final def minute  = minutes

  final def hours = asDuration(HOURS)
  final def hour  = hours

  final def days = asDuration(DAYS)
  final def day  = days

}

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

import scala.math.{ ceil, floor, log, pow }

/**
 * `MathUtils` provides common math functions used in ZIO Test.
 */
private[test] object MathUtils {

  /**
   * Returns the base two logarithm of the specified integer, rounding up to
   * the nearest integer. If the specified integer is zero or negative returns
   * zero.
   */
  final def log2Ceil(n: Int): Int =
    if (n <= 0) 0 else ceil(log(n.toDouble) / log(2.0)).toInt

  /**
   * Returns the base two logarithm of the specified integer, rounding down to
   * the nearest integer. If the specified integer is zero or negative returns
   * zero.
   */
  final def log2Floor(n: Int): Int =
    if (n <= 0) 0 else floor(log(n.toDouble) / log(2.0)).toInt

  /**
   * Returns two raised to the specified power. If the specified power is
   * negative returns zero.
   */
  final def pow2(n: Int): Int =
    pow(2.0, n.toDouble).toInt
}

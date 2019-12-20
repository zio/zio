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

import zio.duration._

import scala.reflect.ClassTag

/**
 * A type of annotation.
 */
final class TestAnnotation[V] private (
  val identifier: String,
  val initial: V,
  val combine: (V, V) => V,
  private val classTag: ClassTag[V]
) {
  override final def equals(that: Any): Boolean = that match {
    case that: TestAnnotation[_] => (identifier, classTag) == ((that.identifier, that.classTag))
  }

  override final lazy val hashCode =
    (identifier, classTag).hashCode
}
object TestAnnotation {

  def apply[V](identifier: String, initial: V, combine: (V, V) => V)(
    implicit classTag: ClassTag[V]
  ): TestAnnotation[V] =
    new TestAnnotation(identifier, initial, combine, classTag)

  /**
   * An annotation which counts ignored tests.
   */
  final val ignored: TestAnnotation[Int] =
    TestAnnotation("ignored", 0, _ + _)

  /**
   * An annotation which counts retried tests.
   */
  final val retried: TestAnnotation[Int] =
    TestAnnotation("retried", 0, _ + _)

  /**
   * An annotation for timing.
   */
  final val timing: TestAnnotation[Duration] =
    TestAnnotation("timing", Duration.Zero, _ + _)
}

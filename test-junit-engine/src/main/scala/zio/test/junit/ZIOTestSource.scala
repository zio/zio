/*
 * Copyright 2024-2024 Vincent Raman and the ZIO Contributors
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

package zio.test.junit

import zio.test._

import java.io.File
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.support.descriptor.{ClassSource, FilePosition, FileSource}

/**
 * Creates the JUnit `TestSource` of a ZIO Test descriptor from the spec class and, for a test, its annotations.
 */
object ZIOTestSource {

  /**
   * Returns a `FileSource` for the file and line of the first location in the `TestAnnotation.trace` annotation of `annotations`, or a `ClassSource` for `testClass` when `annotations` is `None` or has no location.
   */
  def apply(testClass: Class[_], annotations: Option[TestAnnotationMap]): TestSource =
    annotations
      .map(_.get(TestAnnotation.trace))
      .collect { case location :: _ =>
        FileSource.from(new File(location.path), FilePosition.from(location.line))
      }
      .getOrElse(ClassSource.from(testClass))

  /** Returns a `ClassSource` for `testClass`. */
  def apply(testClass: Class[_]): TestSource = ZIOTestSource(testClass, None)

  /**
   * Returns a `FileSource` for the file and line of the first location in the `TestAnnotation.trace` annotation of `annotations`, or a `ClassSource` for `testClass` when there is no location.
   */
  def apply(testClass: Class[_], annotations: TestAnnotationMap): TestSource =
    ZIOTestSource(testClass, Some(annotations))
}

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

import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.{TestDescriptor, TestTag, UniqueId}
import zio.test.{TestAnnotation, TestAnnotationMap}

import java.util
import scala.jdk.CollectionConverters._

/**
 * The JUnit Platform descriptor of one ZIO test, whose display name is the test
 * label. Its tags are the `TestAnnotation.tagged` tags of the test (a tag must
 * be a valid JUnit tag name, for example without spaces). Its source is the
 * file and line where the test is defined (from `TestAnnotation.trace`), or the
 * spec class when that location is missing.
 *
 * @param parent
 *   the parent descriptor
 * @param uniqueId
 *   the unique id of this descriptor
 * @param label
 *   the test label, used as the display name
 * @param testClass
 *   the spec class
 * @param annotations
 *   the annotations of the test
 */
class ZIOTestDescriptor(
  parent: TestDescriptor,
  uniqueId: UniqueId,
  label: String,
  testClass: Class[_],
  annotations: TestAnnotationMap
) extends AbstractTestDescriptor(uniqueId, label, ZIOTestSource(testClass, annotations)) {
  setParent(parent)

  /** Returns `TEST`. */
  override def getType: TestDescriptor.Type = TestDescriptor.Type.TEST

  /**
   * Returns the `TestAnnotation.tagged` tags of the test as JUnit tags. Throws
   * an `org.junit.platform.commons.PreconditionViolationException` when a tag
   * isn't a valid JUnit tag name, for example when it contains a space.
   */
  override def getTags: util.Set[TestTag] =
    annotations.get(TestAnnotation.tagged).map(TestTag.create).asJava
}

/** Constants of [[ZIOTestDescriptor]]. */
object ZIOTestDescriptor {

  /** The unique id segment type of test descriptors: `test`. */
  val segmentType = "test"
}

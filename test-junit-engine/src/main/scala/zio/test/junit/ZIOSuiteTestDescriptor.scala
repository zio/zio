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
import org.junit.platform.engine.{TestDescriptor, UniqueId}

/**
 * The JUnit Platform descriptor of a ZIO Test suite: a container whose display name is the suite label and whose source is the spec class.
 *
 * @param parent
 *   the parent descriptor
 * @param uniqueId
 *   the unique id of this descriptor
 * @param label
 *   the suite label, used as the display name
 * @param testClass
 *   the spec class, used as the test source
 */
class ZIOSuiteTestDescriptor(
  parent: TestDescriptor,
  uniqueId: UniqueId,
  label: String,
  testClass: Class[_]
) extends AbstractTestDescriptor(uniqueId, label, ZIOTestSource(testClass)) {
  setParent(parent)

  /** Returns `CONTAINER`. */
  override def getType: TestDescriptor.Type = TestDescriptor.Type.CONTAINER
}

/** Constants of [[ZIOSuiteTestDescriptor]]. */
object ZIOSuiteTestDescriptor {

  /** The unique id segment type of suite descriptors: `suite`. */
  val segmentType = "suite"
}

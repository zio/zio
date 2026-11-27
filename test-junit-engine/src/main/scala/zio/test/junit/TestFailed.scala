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

import org.opentest4j.AssertionFailedError

/**
 * The failure that the ZIO JUnit engine reports for a failed assertion, and for a test that fails with an error that isn't a `Throwable`. It extends `org.opentest4j.AssertionFailedError`, the assertion failure type of the JUnit Platform.
 *
 * @param message
 *   the rendered failure
 * @param cause
 *   the underlying cause of the failure, or `null`
 */
class TestFailed(message: String, cause: Throwable = null) extends AssertionFailedError(message, cause)

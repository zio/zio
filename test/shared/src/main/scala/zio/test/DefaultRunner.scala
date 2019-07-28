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

import zio.Managed
import zio.internal.{ Platform, PlatformLive }
import zio.test.mock.{ mockEnvironmentManaged, MockConsole, MockEnvironment }

/**
 * A `Runner` that provides a default testable environment.
 */
// TODO: Provide test environment
case class DefaultRunner(
  environment: Managed[Nothing, MockEnvironment] = mockEnvironmentManaged,
  platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ()),
  reporter: Reporter[MockConsole, String] = DefaultReporter.make
) extends Runner[MockEnvironment, String](environment, platform, reporter)

/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.LogLine.Fragment.Style
import zio.test.render.LogLine.{Fragment, Line}

/**
 * Rendering of test results: the [[zio.test.render.TestRenderer]]
 * implementations, the [[zio.test.render.LogLine]] values they build, and
 * helpers that create styled fragments.
 */
package object render {

  /** Creates a fragment with `s` in the `Info` style. */
  def info(s: String): Fragment = Fragment(s, Style.Info)

  /** Creates a fragment with `s` in the `Error` style. */
  def error(s: String): Fragment = Fragment(s, Style.Error)

  /** Creates a fragment with `s` in the `Warning` style. */
  def warn(s: String): Fragment = Fragment(s, Style.Warning)

  /** Creates a fragment with `s` in the `Primary` style. */
  def primary(s: String): Fragment = Fragment(s, Style.Primary)

  /** Creates a fragment with `s` in the `Detail` style. */
  def detail(s: String): Fragment = Fragment(s, Style.Detail)

  /** Creates a fragment with `s` in the `Default` style. */
  def fr(s: String): Fragment = Fragment(s, Style.Default)

  /** Creates a fragment with `s` in the `Dimmed` style. */
  def dim(s: String): Fragment = Fragment(s, Style.Dimmed)

  /**
   * Creates a fragment with `s` in the `Bold` style, wrapping the `Default`
   * style.
   */
  def bold(s: String): Fragment = fr(s).bold

  /**
   * Creates a fragment with `s` in the `Underlined` style, wrapping the
   * `Default` style.
   */
  def underlined(s: String): Fragment = fr(s).underlined

  /**
   * Creates a fragment with `s` in the `Ansi` style with `ansiColor`, wrapping
   * the `Default` style.
   */
  def ansi(s: String, ansiColor: String): Fragment = fr(s).ansi(ansiColor)

  /** A fragment that holds a single space in the `Default` style. */
  val sp: Fragment = Fragment(" ")

  /** Adds `i` to the offset of `line`; the same as `line.withOffset(i)`. */
  def withOffset(i: Int)(line: LogLine.Line): Line = line.withOffset(i)
}

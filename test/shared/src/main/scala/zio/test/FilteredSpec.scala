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

import zio.Trace

/**
 * Filters a given `Spec` based on the command-line arguments. If no arguments
 * were specified, the spec returns unchanged.
 */
private[zio] object FilteredSpec {

  def apply[R, E](spec: Spec[R, E], args: TestArgs)(implicit trace: Trace): Spec[R, E] =
    (
      searchTermFilter[R, E](args)
        andThen tagIgnoreFilter[R, E](args)
        andThen tagSearchFilter[R, E](args)
    ).apply(spec)

  private def searchTermFilter[R, E](args: TestArgs)(implicit trace: Trace): Spec[R, E] => Spec[R, E] =
    spec =>
      args.testSearchTerms match {
        case Nil => spec
        case testSearchTerms =>
          spec
            .filterLabels(
              label => testSearchTerms.exists(term => label.contains(term)),
              prefix = "",
              accumulatePrefix = true
            )
            .getOrElse(Spec.empty)
      }

  private def tagIgnoreFilter[R, E](args: TestArgs)(implicit trace: Trace): Spec[R, E] => Spec[R, E] =
    spec =>
      args.tagIgnoreTerms match {
        case Nil => spec
        case tagIgnoreTerms =>
          spec
            .filterNotTags(tag => tagIgnoreTerms.contains(tag))
            .getOrElse(Spec.empty)
      }

  private def tagSearchFilter[R, E](args: TestArgs)(implicit trace: Trace): Spec[R, E] => Spec[R, E] =
    spec =>
      args.tagSearchTerms match {
        case Nil => spec
        case tagSearchTerms =>
          spec
            .filterTags(tag => tagSearchTerms.contains(tag))
            .getOrElse(Spec.empty)
      }
}

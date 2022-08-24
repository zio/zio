/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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
import zio.Trace

/**
 * Filters a given `Spec` based on the command-line arguments. If no arguments
 * were specified, the spec returns unchanged.
 */
private[zio] object FilteredSpec {

  def apply[R, E](spec: Spec[R, E], args: TestArgs)(implicit trace: Trace): Spec[R, E] = {
    val testSearchedSpec = args.testSearchTerms match {
      case Nil             => spec
      case testSearchTerms => spec.filterTags(testSearchTerms.contains(_)).getOrElse(Spec.empty)
    }
    val tagSearchedSpec = args.tagSearchTerms match {
      case Nil => testSearchedSpec
      case tagSearchTerms =>
        testSearchedSpec
          .filterLabels(label => tagSearchTerms.exists(term => label.contains(term)))
          .getOrElse(Spec.empty)
    }
    args.tagIgnoreTerms match {
      case Nil => tagSearchedSpec
      case tagIgnoreTerms =>
        tagSearchedSpec
          .filterLabels(label => !tagIgnoreTerms.exists(term => label.contains(term)))
          .getOrElse(Spec.empty)
    }
  }
}

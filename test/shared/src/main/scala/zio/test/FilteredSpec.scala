/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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
import zio.ZTraceElement

/**
 * Filters a given `ZSpec` based on the command-line arguments. If no arguments
 * were specified, the spec returns unchanged.
 */
private[zio] object FilteredSpec {
  def apply[R, E](spec: ZSpec[R, E], args: TestArgs)(implicit trace: ZTraceElement): ZSpec[R, E] =
    (args.testSearchTerms, args.tagSearchTerms) match {
      case (Nil, Nil) =>
        spec
      case (testSearchTerms, Nil) =>
        spec
          .filterLabels(label => testSearchTerms.exists(term => label.contains(term)))
          .getOrElse(Spec.empty)
      case (Nil, tagSearchTerms) =>
        spec
          .filterTags(tag => tagSearchTerms.contains(tag))
          .getOrElse(Spec.empty)
      case (testSearchTerms, tagSearchTerms) =>
        spec
          .filterTags(tag => testSearchTerms.contains(tag))
          .flatMap(_.filterLabels(label => tagSearchTerms.exists(term => label.contains(term))))
          .getOrElse(Spec.empty)
    }
}

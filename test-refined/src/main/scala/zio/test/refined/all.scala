/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.refined

import zio.test.refined.types.AllTypesInstances

/**
 * Brings the implicit `DeriveGen` instances of this module into scope. `import
 * zio.test.refined.all._` lets `DeriveGen` derive generators for refined types
 * such as `Int Refined Greater[N]`, `Char Refined Digit`, `List[A] Refined
 * NonEmpty`, `String Refined StartsWith[S]`, `Refined[T, A Or B]`, `Refined[T,
 * Equal[U]]` and the aliases of `eu.timepit.refined.types` (for example
 * `PosInt`, `NonEmptyString`, `Month` and `MD5`). The `*Gen` members (for
 * example `intGreaterThanGen`) return plain `Gen` values, and the implicit
 * members wrap them as `DeriveGen`.
 *
 * The generators build the refined value with `Refined.unsafeApply`, so the
 * predicate is not checked. The documentation of each member gives the range or
 * shape it produces.
 *
 * {{{
 * import eu.timepit.refined.types.numeric.PosInt
 * import zio.test._
 * import zio.test.magnolia.DeriveGen
 * import zio.test.refined.all._
 *
 * val spec: Spec[Any, Nothing] =
 *   test("a PosInt is positive") {
 *     check(DeriveGen[PosInt])(n => assertTrue(n.value > 0))
 *   }
 * }}}
 */
object all
    extends BooleanInstances
    with CharInstances
    with CollectionInstances
    with GenericInstances
    with StringInstances
    with NumericInstances
    with AllTypesInstances

/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio

package object optics extends EitherCompat {

  type ZIso[-S, +T, +A, -B]       = ZOptic[S, Any, B, Nothing, Nothing, A, T]
  type ZLens[-S, +T, +A, -B]      = ZOptic[S, S, B, Nothing, Nothing, A, T]
  type ZOptional[-S, +T, +A, -B]  = ZOptic[S, S, B, Unit, Unit, A, T]
  type ZPrism[-S, +T, +A, -B]     = ZOptic[S, Any, B, Unit, Nothing, A, T]
  type ZTraversal[-S, +T, +A, -B] = ZOptic[S, S, List[B], Nothing, Unit, List[A], T]

  type Iso[S, A]       = ZIso[S, S, A, A]
  type Lens[S, A]      = ZLens[S, S, A, A]
  type Optional[S, A]  = ZOptional[S, S, A, A]
  type Prism[S, A]     = ZPrism[S, S, A, A]
  type Traversal[S, A] = ZTraversal[S, S, A, A]
}

/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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
package interop
package bio

abstract class RunSync2[F[+_, +_]] extends Sync2[F] {

  def runSync[G[+_, +_], E, A](fa: F[E, A])(implicit SG: Sync2[G]): G[E, A]
}

object RunSync2 {

  @inline def apply[F[+_, +_]: RunSync2]: RunSync2[F] = implicitly
}

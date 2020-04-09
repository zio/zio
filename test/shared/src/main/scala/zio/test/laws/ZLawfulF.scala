/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.laws

object ZLawfulF {

  trait Covariant[-Caps[_[+_]], -R] { self =>
    def laws: ZLawsF.Covariant[Caps, R]
    def +[Caps1[x[+_]] <: Caps[x], R1 <: R](that: Covariant[Caps1, R1]): Covariant[Caps1, R1] =
      new Covariant[Caps1, R1] {
        val laws = self.laws + that.laws
      }
  }

  trait Contravariant[-Caps[_[-_]], -R] { self =>
    def laws: ZLawsF.Contravariant[Caps, R]
    def +[Caps1[x[-_]] <: Caps[x], R1 <: R](that: Contravariant[Caps1, R1]): Contravariant[Caps1, R1] =
      new Contravariant[Caps1, R1] {
        val laws = self.laws + that.laws
      }
  }

  trait Invariant[-Caps[_[_]], -R] { self =>
    def laws: ZLawsF.Invariant[Caps, R]
    def +[Caps1[x[_]] <: Caps[x], R1 <: R](that: Invariant[Caps1, R1]): Invariant[Caps1, R1] =
      new Invariant[Caps1, R1] {
        val laws = self.laws + that.laws
      }
  }
}

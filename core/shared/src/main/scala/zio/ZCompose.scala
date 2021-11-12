/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

trait ZCompose[+LeftLower, -LeftUpper, LeftOut[In], +RightLower, -RightUpper, RightOut[In]] {
  type Lower
  type Upper
  type Out[In]
}

object ZCompose extends ComposeLowPriorityImplicits {
  type WithOut[LeftLower, LeftUpper, LeftOut[In], RightLower, RightUpper, RightOut[In], Lower0, Upper0, Out0[In]] =
    ZCompose[LeftLower, LeftUpper, LeftOut, RightLower, RightUpper, RightOut] {
      type Lower   = Lower0
      type Upper   = Upper0
      type Out[In] = Out0[In]
    }

  implicit def compose[
    LeftLower,
    LeftUpper,
    LeftOut >: RightLower <: RightUpper,
    RightLower,
    RightUpper,
    RightOut
  ]: ZCompose.WithOut[
    LeftLower,
    LeftUpper,
    ({ type Out[In] = LeftOut })#Out,
    RightLower,
    RightUpper,
    ({ type Out[In] = RightOut })#Out,
    LeftLower,
    LeftUpper,
    ({ type Out[In] = RightOut })#Out
  ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out
    ] {
      type Lower   = LeftLower
      type Upper   = LeftUpper
      type Out[In] = RightOut
    }

  implicit def identity[LeftLower <: RightLower, LeftUpper, RightLower, RightUpper]: ZCompose.WithOut[
    LeftLower,
    LeftUpper,
    Identity,
    RightLower,
    RightUpper,
    Identity,
    RightLower,
    LeftUpper with RightUpper,
    Identity
  ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      Identity
    ] {
      type Lower   = RightLower
      type Upper   = LeftUpper with RightUpper
      type Out[In] = In
    }

  implicit def leftIdentity[LeftLower <: RightLower, LeftUpper, RightLower, RightUpper, RightOut]: ZCompose.WithOut[
    LeftLower,
    LeftUpper,
    Identity,
    RightLower,
    RightUpper,
    ({ type Out[In] = RightOut })#Out,
    RightLower,
    LeftUpper with RightUpper,
    ({ type Out[In] = RightOut })#Out
  ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out
    ] {
      type Lower   = RightLower
      type Upper   = LeftUpper with RightUpper
      type Out[In] = RightOut
    }

  implicit def rightIdentity[LeftLower, LeftUpper, LeftOut >: RightLower <: RightUpper, RightLower, RightUpper]
    : ZCompose.WithOut[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      Identity,
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out
    ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      Identity
    ] {
      type Lower   = LeftLower
      type Upper   = LeftUpper
      type Out[In] = LeftOut
    }
}

trait ComposeLowPriorityImplicits {

  type Identity[A] = A

  implicit def identityLowPriority[LeftLowerElem, LeftUpperElem, RightLowerElem <: LeftLowerElem, RightUpperElem]
    : ZCompose.WithOut[
      LeftLowerElem,
      LeftUpperElem,
      Identity,
      RightLowerElem,
      RightUpperElem,
      Identity,
      LeftLowerElem,
      LeftUpperElem with RightUpperElem,
      Identity
    ] =
    new ZCompose[
      LeftLowerElem,
      LeftUpperElem,
      Identity,
      RightLowerElem,
      RightUpperElem,
      Identity
    ] {
      type Lower   = LeftLowerElem
      type Upper   = LeftUpperElem with RightUpperElem
      type Out[In] = In
    }

  implicit def leftIdentityLowPriority[LeftLower, LeftUpper, RightLower <: LeftLower, RightUpper, RightOut]
    : ZCompose.WithOut[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
      LeftLower,
      LeftUpper with RightUpper,
      ({ type Out[In] = RightOut })#Out
    ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out
    ] {
      type Lower   = LeftLower
      type Upper   = LeftUpper with RightUpper
      type Out[In] = RightOut
    }
}

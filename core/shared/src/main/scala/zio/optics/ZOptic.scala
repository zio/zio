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

package zio.optics

/**
 * A `ZOptic` is able to get and set a piece of a whole, possibly failing. In
 * the most general possible case, the set / get types are distinct, and
 * setting may fail with a different error than getting.
 *
 * See more specific type aliases for concrete examples of what optics can be
 * used to do.
 */
final case class ZOptic[-GetWhole, -SetWholeBefore, -SetPiece, +GetError, +SetError, +GetPiece, +SetWholeAfter](
  getEither: GetWhole => Either[GetError, GetPiece],
  setEither: SetPiece => SetWholeBefore => Either[SetError, SetWholeAfter]
) { self =>

  /**
   * Composes this optic with the specified optic where the specified optic
   * does not require access to the state for its set operation.
   */
  def composePrism[SetPiece1, GetError1 >: GetError, SetError1 >: SetError, GetPiece1](
    that: ZOptic[GetPiece, Any, SetPiece1, GetError1, SetError1, GetPiece1, SetPiece]
  ): ZOptic[GetWhole, SetWholeBefore, SetPiece1, GetError1, SetError1, GetPiece1, SetWholeAfter] =
    ZOptic(
      s => self.getEither(s).flatMap(that.getEither),
      b => s => that.setEither(b)(s).flatMap(self.setEither(_)(s))
    )
}

object ZOptic {

  implicit class ZOpticSyntax[
    GetWhole,
    SetWholeBefore <: GetWhole,
    SetPiece,
    GetError <: SetError,
    SetError,
    GetPiece,
    SetWholeAfter
  ](private val self: ZOptic[GetWhole, SetWholeBefore, SetPiece, GetError, SetError, GetPiece, SetWholeAfter])
      extends AnyVal {

    /**
     * Compose this optic with the specified optic where the specified optic
     * requires access to the state for its set operation.
     */
    def composeLens[SetPiece1, GetError1 >: GetError, SetError1 >: SetError, GetPiece1](
      that: ZOptic[GetPiece, GetPiece, SetPiece1, GetError1, SetError1, GetPiece1, SetPiece]
    ): ZOptic[GetWhole, SetWholeBefore, SetPiece1, GetError1, SetError1, GetPiece1, SetWholeAfter] =
      ZOptic(
        s => self.getEither(s).flatMap(that.getEither),
        b => s => self.getEither(s).flatMap(that.setEither(b)).flatMap(self.setEither(_)(s))
      )
  }
}

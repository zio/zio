/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

import izumi.reflect.macrortti.LightTypeTagRef

private[zio] trait VersionSpecific {

  type Tag[A] = izumi.reflect.Tag[A]
  lazy val Tag = izumi.reflect.Tag

  type TagK[F[_]] = izumi.reflect.TagK[F]
  lazy val TagK = izumi.reflect.TagK

  type TagKK[F[_, _]] = izumi.reflect.TagKK[F]
  lazy val TagKK = izumi.reflect.TagKK

  type TagK3[F[_, _, _]] = izumi.reflect.TagK3[F]
  lazy val TagK3 = izumi.reflect.TagK3

  type TagK4[F[_, _, _, _]]                                                        = izumi.reflect.TagK4[F]
  type TagK5[F[_, _, _, _, _]]                                                     = izumi.reflect.TagK5[F]
  type TagK6[F[_, _, _, _, _, _]]                                                  = izumi.reflect.TagK6[F]
  type TagK7[F[_, _, _, _, _, _, _]]                                               = izumi.reflect.TagK7[F]
  type TagK8[F[_, _, _, _, _, _, _, _]]                                            = izumi.reflect.TagK8[F]
  type TagK9[F[_, _, _, _, _, _, _, _, _]]                                         = izumi.reflect.TagK9[F]
  type TagK10[F[_, _, _, _, _, _, _, _, _, _]]                                     = izumi.reflect.TagK10[F]
  type TagK11[F[_, _, _, _, _, _, _, _, _, _, _]]                                  = izumi.reflect.TagK11[F]
  type TagK12[F[_, _, _, _, _, _, _, _, _, _, _, _]]                               = izumi.reflect.TagK12[F]
  type TagK13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]]                            = izumi.reflect.TagK13[F]
  type TagK14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]]                         = izumi.reflect.TagK14[F]
  type TagK15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                      = izumi.reflect.TagK15[F]
  type TagK16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                   = izumi.reflect.TagK16[F]
  type TagK17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                = izumi.reflect.TagK17[F]
  type TagK18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]             = izumi.reflect.TagK18[F]
  type TagK19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]          = izumi.reflect.TagK19[F]
  type TagK20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]       = izumi.reflect.TagK20[F]
  type TagK21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]    = izumi.reflect.TagK21[F]
  type TagK22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK22[F]

  type LightTypeTag = izumi.reflect.macrortti.LightTypeTag

  private[zio] def taggedIsSubtype(left: LightTypeTag, right: LightTypeTag): Boolean =
    left <:< right

  private[zio] def taggedTagType[A](tagged: Tag[A]): LightTypeTag =
    tagged.tag

  /**
   * This method takes a tag for an intersection of [[zio.Has]]
   * and returns a set of tags for parameters of each individual `Has`:
   *
   * `Tag[Has[A] with Has[B]]` should produce `Set(Tag[A], Tag[B])`
   */
  private[zio] def taggedGetHasServices[A](t: LightTypeTag): Set[LightTypeTag] =
    t.decompose.map { parent =>
      parent.ref match {
        case reference: LightTypeTagRef.AppliedNamedReference if reference.typeArgs.size == 1 =>
          parent.typeArgs.head

        case _ =>
          parent
      }
    }
}

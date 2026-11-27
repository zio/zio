/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentHashMap

private[zio] trait VersionSpecific {

  /**
   * The izumi-reflect type tag for `A`, an alias for `izumi.reflect.Tag`. ZIO's
   * `Tag`, which `ZEnvironment` uses to store and look up services, extends it.
   */
  type EnvironmentTag[A] = izumi.reflect.Tag[A]

  /**
   * The companion of `EnvironmentTag`: `EnvironmentTag[A]` summons the tag for
   * `A`, and `EnvironmentTag[A].tag` is its `LightTypeTag`.
   */
  val EnvironmentTag = izumi.reflect.Tag

  /**
   * A type tag for a type constructor with one type parameter, such as
   * `Option`. An alias for izumi-reflect's `izumi.reflect.TagK`.
   */
  type TagK[F[_]] = izumi.reflect.TagK[F]

  /**
   * The companion of `TagK`: `TagK[Option]` summons the tag for `Option`.
   */
  val TagK = izumi.reflect.TagK

  /**
   * A type tag for a type constructor with two type parameters, such as
   * `Either`. An alias for izumi-reflect's `izumi.reflect.TagKK`.
   */
  type TagKK[F[_, _]] = izumi.reflect.TagKK[F]

  /**
   * The companion of `TagKK`: `TagKK[Either]` summons the tag for `Either`.
   */
  val TagKK = izumi.reflect.TagKK

  /**
   * A type tag for a type constructor with three type parameters, such as
   * `ZIO`. An alias for izumi-reflect's `izumi.reflect.TagK3`.
   */
  type TagK3[F[_, _, _]] = izumi.reflect.TagK3[F]

  /**
   * The companion of `TagK3`: `TagK3[ZIO]` summons the tag for `ZIO`.
   */
  val TagK3 = izumi.reflect.TagK3

  /**
   * A type tag for a type constructor with 4 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK4`.
   */
  type TagK4[F[_, _, _, _]] = izumi.reflect.TagK4[F]

  /**
   * A type tag for a type constructor with 5 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK5`.
   */
  type TagK5[F[_, _, _, _, _]] = izumi.reflect.TagK5[F]

  /**
   * A type tag for a type constructor with 6 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK6`.
   */
  type TagK6[F[_, _, _, _, _, _]] = izumi.reflect.TagK6[F]

  /**
   * A type tag for a type constructor with 7 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK7`.
   */
  type TagK7[F[_, _, _, _, _, _, _]] = izumi.reflect.TagK7[F]

  /**
   * A type tag for a type constructor with 8 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK8`.
   */
  type TagK8[F[_, _, _, _, _, _, _, _]] = izumi.reflect.TagK8[F]

  /**
   * A type tag for a type constructor with 9 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK9`.
   */
  type TagK9[F[_, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK9[F]

  /**
   * A type tag for a type constructor with 10 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK10`.
   */
  type TagK10[F[_, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK10[F]

  /**
   * A type tag for a type constructor with 11 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK11`.
   */
  type TagK11[F[_, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK11[F]

  /**
   * A type tag for a type constructor with 12 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK12`.
   */
  type TagK12[F[_, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK12[F]

  /**
   * A type tag for a type constructor with 13 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK13`.
   */
  type TagK13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK13[F]

  /**
   * A type tag for a type constructor with 14 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK14`.
   */
  type TagK14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK14[F]

  /**
   * A type tag for a type constructor with 15 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK15`.
   */
  type TagK15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK15[F]

  /**
   * A type tag for a type constructor with 16 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK16`.
   */
  type TagK16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK16[F]

  /**
   * A type tag for a type constructor with 17 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK17`.
   */
  type TagK17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK17[F]

  /**
   * A type tag for a type constructor with 18 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK18`.
   */
  type TagK18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK18[F]

  /**
   * A type tag for a type constructor with 19 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK19`.
   */
  type TagK19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK19[F]

  /**
   * A type tag for a type constructor with 20 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK20`.
   */
  type TagK20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK20[F]

  /**
   * A type tag for a type constructor with 21 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK21`.
   */
  type TagK21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK21[F]

  /**
   * A type tag for a type constructor with 22 type parameters. An alias for
   * izumi-reflect's `izumi.reflect.TagK22`.
   */
  type TagK22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK22[F]

  /**
   * The runtime representation of a type that an `EnvironmentTag` carries
   * (`EnvironmentTag[A].tag`), which ZIO uses as the key of each service in a
   * `ZEnvironment`. An alias for izumi-reflect's
   * `izumi.reflect.macrortti.LightTypeTag`.
   */
  type LightTypeTag = izumi.reflect.macrortti.LightTypeTag

  private[zio] def taggedIsSubtype(left: LightTypeTag, right: LightTypeTag): Boolean = {
    // NOTE: Prefer get/putIfAbsent pattern as it offers better read performance at the cost of
    // potentially computing `<:<` multiple times during app warmup
    val k = (left, right)
    taggedSubtypes.get(k) match {
      case null =>
        val v = left <:< right
        taggedSubtypes.putIfAbsent(k, v)
        v
      case v => v.booleanValue()
    }
  }

  private[zio] def taggedTagType[A](tagged: EnvironmentTag[A]): LightTypeTag =
    tagged.tag

  /**
   * Returns the `LightTypeTag` of each type in the intersection type `t`: for
   * the tag of `A with B` it returns the tags of `A` and `B`, and for a type
   * that is not an intersection, a set that holds `t` alone. It caches each
   * result.
   */
  private[zio] def taggedGetServices[A](t: LightTypeTag): Set[LightTypeTag] =
    // NOTE: See `taggedIsSubtype` for implementation notes
    taggedServices.get(t) match {
      case null =>
        val v = t.decompose
        taggedServices.putIfAbsent(t, v)
        v
      case v => v
    }

  private val taggedSubtypes: ConcurrentHashMap[(LightTypeTag, LightTypeTag), java.lang.Boolean] =
    /*
     * '''NOTE''': Larger maps have lower chance of collision which offers better
     * read performance and smaller chance of entering synchronized blocks during writes
     */
    new ConcurrentHashMap[(LightTypeTag, LightTypeTag), java.lang.Boolean](1024)

  private val taggedServices: ConcurrentHashMap[LightTypeTag, Set[LightTypeTag]] =
    new ConcurrentHashMap[LightTypeTag, Set[LightTypeTag]](256)

  private sealed trait BoxedBool { self =>
    final def value: Boolean = self eq BoxedBool.True
  }
  private object BoxedBool {
    case object True  extends BoxedBool
    case object False extends BoxedBool
  }
}

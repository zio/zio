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

import zio.blocking.Blocking
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.random.Random
import zio.scheduler.Scheduler
import zio.system.System

private[zio] trait PlatformSpecific {
  type ZEnv = Clock with Console with System with Random with Scheduler with Blocking

  object ZEnv {
    val any: ZLayer[ZEnv, Nothing, ZEnv] =
      ZLayer.requires[ZEnv]
    val live: ZLayer.NoDeps[Nothing, ZEnv] =
      (Scheduler.live >>> Clock.live) ++ Console.live ++ System.live ++ Random.live ++ Scheduler.live ++ Blocking.live
  }

  type Tagged[A] = ScalaSpecific.Tagged[A]
  type TagType   = ScalaSpecific.TagType

  private[zio] def taggedTagType[A](t: Tagged[A]): TagType = ScalaSpecific.taggedTagType(t)

  private[zio] def taggedIsSubtype(left: TagType, right: TagType): Boolean = ScalaSpecific.taggedIsSubtype(left, right)

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] = ScalaSpecific.taggedGetHasServices(t)
}

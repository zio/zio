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

final case class ZDep[-RIn <: Has[_], +E, +ROut <: Has[_]](value: ZManaged[RIn, E, ROut]) { self =>
  def >>>[E1 >: E, ROut2 <: Has[_]](that: ZDep[ROut, E1, ROut2]): ZDep[RIn, E1, ROut2] =
    ZDep(self.value.flatMap(v => that.value.provide(v)))

  def ***[E1 >: E, RIn2 <: Has[_], ROut2 <: Has[_]](
    that: ZDep[RIn2, E1, ROut2]
  ): ZDep[RIn with RIn2, E1, ROut with ROut2] =
    ZDep(
      ZManaged.accessManaged[RIn with RIn2] { env =>
        (self.value.provide(env) zipWith that.value.provide(env))((l, r) => l.++[ROut2](r))
      }
    )

  def build[RIn2 <: RIn](implicit ev: Has.Any =:= RIn2): Managed[E, ROut] = value.provide(ev(Has.any))
}
object ZDep {
  def succeed[A: Tagged](a: A): ZDep[Has.Any, Nothing, Has[A]] = ZDep(ZManaged.succeed(Has(a)))

  def dep[A: Tagged, E, B <: Has[_]: Tagged](f: A => B): ZDep[Has[A], E, B] =
    ZDep[Has[A], E, B](ZManaged.fromEffect(ZIO.access[Has[A]](m => f(m.get))))

  trait Clock; trait Console; trait Scheduler; trait DBConfig; trait Database

  val liveScheduler: ZDep[Has.Any, Nothing, Has[Scheduler]]                 = ???
  val liveDbConfig: ZDep[Has.Any, Nothing, Has[DBConfig]]                   = ???
  val liveClock: ZDep[Has[Scheduler], Nothing, Has[Clock]]                  = ZDep.dep((v: Scheduler) => { val _ = v; ??? })
  val liveConsole: ZDep[Has.Any, Nothing, Has[Console]]                     = ???
  val liveDatabase: ZDep[Has[DBConfig], java.io.IOException, Has[Database]] = ???

  val dependencies =
    (liveDbConfig >>> liveDatabase) ***
      (liveScheduler >>> liveClock) ***
      liveConsole

  // myProgram.provideDep(dependencies)
}

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

package scalaz.zio.stream

import scalaz.zio._

trait SinkPure[+E, +A0, -A, +B] extends SinkR[Any, E, A0, A, B] { self =>
  import SinkR.Step

  override def initial              = IO.succeed(initialPure)
  override def step(s: State, a: A) = IO.succeed(stepPure(s, a))
  override def extract(s: State)    = IO.fromEither(extractPure(s))

  def initialPure: Step[State, Nothing]
  def stepPure(s: State, a: A): Step[State, A0]
  def extractPure(s: State): Either[E, B]

  def stepChunkPure[A1 <: A](s: State, as: Chunk[A1]): Step[State, A0] = {
    val len = as.length

    def loop(s: Step[State, A0], i: Int): Step[State, A0] =
      if (i >= len) s
      else if (Step.cont(s)) loop(stepPure(Step.state(s), as(i)), i + 1)
      else s

    loop(Step.more(s), 0)
  }

  override def map[C](f: B => C): SinkPure[E, A0, A, C] =
    new SinkPure[E, A0, A, C] {
      type State = self.State
      val initialPure              = self.initialPure
      def stepPure(s: State, a: A) = self.stepPure(s, a)
      def extractPure(s: State)    = self.extractPure(s).map(f)
    }

  override def filter[A1 <: A](f: A1 => Boolean): SinkPure[E, A0, A1, B] =
    new SinkPure[E, A0, A1, B] {
      type State = self.State
      val initialPure = self.initialPure

      def stepPure(state: State, a: A1) =
        if (f(a)) self.stepPure(state, a)
        else Step.more(state)

      def extractPure(state: State) = self.extractPure(state)
    }

  override def contramap[C](f: C => A): SinkPure[E, A0, C, B] =
    new SinkPure[E, A0, C, B] {
      type State = self.State
      val initialPure = self.initialPure
      def stepPure(s: State, c: C) =
        self.stepPure(s, f(c))
      def extractPure(s: State) = self.extractPure(s)
    }

  override def dimap[C, D](f: C => A)(g: B => D): SinkPure[E, A0, C, D] =
    new SinkPure[E, A0, C, D] {
      type State = self.State
      val initialPure = self.initialPure
      def stepPure(s: State, c: C) =
        self.stepPure(s, f(c))
      def extractPure(s: State) = self.extractPure(s).map(g)
    }

  override def mapRemainder[A1](f: A0 => A1): SinkPure[E, A1, A, B] =
    new SinkPure[E, A1, A, B] {
      type State = self.State
      val initialPure = self.initialPure
      def stepPure(s: State, a: A) =
        Step.map(self.stepPure(s, a))(f)
      def extractPure(s: State) = self.extractPure(s)
    }
}

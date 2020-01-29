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

package zio.stream

import zio._

private[stream] trait SinkPure[+E, +A0, -A, +B] extends ZSink[Any, E, A0, A, B] { self =>

  override def contramap[C](f: C => A): SinkPure[E, A0, C, B] =
    new SinkPure[E, A0, C, B] {
      type State = self.State
      val initialPure                  = self.initialPure
      def stepPure(state: State, c: C) = self.stepPure(state, f(c))
      def extractPure(state: State)    = self.extractPure(state)
      def cont(state: State)           = self.cont(state)
    }

  override def dimap[C, D](f: C => A)(g: B => D): SinkPure[E, A0, C, D] =
    new SinkPure[E, A0, C, D] {
      type State = self.State
      val initialPure                  = self.initialPure
      def stepPure(state: State, c: C) = self.stepPure(state, f(c))
      def extractPure(state: State)    = self.extractPure(state).map { case (b, leftover) => (g(b), leftover) }
      def cont(state: State)           = self.cont(state)
    }

  def extract(state: State) = IO.fromEither(extractPure(state))

  def extractPure(state: State): Either[E, (B, Chunk[A0])]

  def initial = IO.succeedNow(initialPure)

  def initialPure: State

  override def map[C](f: B => C): SinkPure[E, A0, A, C] =
    new SinkPure[E, A0, A, C] {
      type State = self.State
      val initialPure                  = self.initialPure
      def stepPure(state: State, a: A) = self.stepPure(state, a)
      def extractPure(state: State)    = self.extractPure(state).map { case (b, leftover) => (f(b), leftover) }
      def cont(state: State)           = self.cont(state)
    }

  override final def mapRemainder[A1](f: A0 => A1): SinkPure[E, A1, A, B] =
    new SinkPure[E, A1, A, B] {
      type State = self.State
      val initialPure                  = self.initialPure
      def stepPure(state: State, a: A) = self.stepPure(state, a)
      def extractPure(state: State)    = self.extractPure(state).map { case (b, leftover) => (b, leftover.map(f)) }
      def cont(state: State)           = self.cont(state)
    }

  override final def step(s: State, a: A) = IO.succeedNow(stepPure(s, a))

  final def stepChunkPure[A00 >: A0, A1 <: A](state: State, as: Chunk[A1])(
    implicit ev: A1 =:= A00
  ): (State, Chunk[A00]) = {
    val len = as.length

    def loop(state: State, i: Int): (State, Chunk[A00]) =
      if (i >= len) (state, Chunk.empty)
      else if (self.cont(state)) loop(stepPure(state, as(i)), i + 1)
      else (state, as.map(ev).splitAt(i)._2)

    loop(state, 0)
  }

  def stepPure(s: State, a: A): State
}

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

import zio.internal.macros.LayerMacros

private[zio] trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * trait Database { def query(sql: String): UIO[Int] }
   * trait Mailer { def send(to: String): UIO[Unit] }
   *
   * val databaseLayer: ULayer[Database] =
   *   ZLayer.succeed(new Database { def query(sql: String): UIO[Int] = ZIO.succeed(42) })
   *
   * val report: ZIO[Database with Mailer, Nothing, Unit] =
   *   ZIO.serviceWithZIO[Database](_.query("select count(*) from orders")) *>
   *     ZIO.serviceWithZIO[Mailer](_.send("ops@example.com"))
   *
   * val needsMailer: ZIO[Mailer, Nothing, Unit] = report.provideSome[Mailer](databaseLayer)
   * }}}
   */
  def provideSome[R0]: ProvideSomeLayerPartiallyApplied[R0, R, E, A] =
    new ProvideSomeLayerPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a layer for the ZIO effect from the given layers,
   * providing its whole environment, and returns an effect with no
   * requirements. A macro wires the layers by their input and output types at
   * compile time: a missing layer, two layers for the same type or a circular
   * dependency is a compile error, and an unused layer is a compile warning.
   *
   * {{{
   * trait Inventory { def count(sku: String): UIO[Int] }
   *
   * val inventoryLayer: ULayer[Inventory] =
   *   ZLayer.succeed(new Inventory { def count(sku: String): UIO[Int] = ZIO.succeed(7) })
   *
   * val stock: UIO[Int] =
   *   ZIO.serviceWithZIO[Inventory](_.count("SKU-2002")).provide(inventoryLayer)
   * }}}
   */
  def provide[E1 >: E](layer: ZLayer[_, E1, _]*): ZIO[Any, E1, A] =
    macro LayerMacros.provideImpl[ZIO, R, E1, A]

}

/**
 * The result of `ZIO#provideSome[R0]`: call it with the layers that build the
 * part of the environment that `R0` does not cover.
 */
final class ProvideSomeLayerPartiallyApplied[R0, -R, +E, +A](val self: ZIO[R, E, A]) extends AnyVal {

  /**
   * Provides the environment with `layer`, which takes `R0` as input, and
   * returns an effect that requires only `R0`.
   */
  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit trace: Trace): ZIO[R0, E1, A] =
    self.provideLayer(layer)

  /**
   * Returns the builder of `ZIO#provideSomeLayer` for the remainder type `R0`
   * given here; the type given to `provideSome` is not used.
   */
  def provideSomeLayer[R0]: ZIO.ProvideSomeLayer[R0, R, E, A] =
    new ZIO.ProvideSomeLayer[R0, R, E, A](self)

  /**
   * Builds the part of the environment that `R0` does not cover from the
   * given layers, wiring them at compile time, and returns an effect that
   * requires only `R0`. A missing layer is a compile error and an unused layer
   * is a compile warning.
   */
  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): ZIO[R0, E1, A] =
    macro LayerMacros.provideSomeImpl[ZIO, R0, R, E1, A]
}

package zio.tagging.internal

import zio.tagging.tag.@@
import zio.{EnvironmentTag, Tag, ZEnvironment, ZLayer}

sealed trait ZLayerRequireTagged[RIn, A, T] {
  type ROut

  def apply[E, X](layer: ZLayer[RIn, E, X]): ZLayer[ROut, E, X]
}

object ZLayerRequireTagged {
  type Aux[RIn, A, T, ROut0] = ZLayerRequireTagged[RIn, A, T] { type ROut = ROut0 }

  implicit def instance[A, T, RIn <: A, MinusOut](implicit
    A: Tag[A],
    AT: Tag[A @@ T],
    minus: Minus.Aux[RIn, A, MinusOut],
    MinusOut: EnvironmentTag[MinusOut]
  ): Aux[RIn, A, T, (A @@ T) with MinusOut] =
    new ZLayerRequireTagged[RIn, A, T] {
      override type ROut = (A @@ T) with MinusOut

      override def apply[E, X](layer: ZLayer[RIn, E, X]): ZLayer[ROut, E, X] = {
        val l = ZLayer.fromFunction((a: A @@ T) => a: A)
        val o = ZLayer.environment[minus.Out].map(_.customPrune[minus.Out])

        val env = minus.evidence.substituteCo(l +!+ o)
        env >>> layer
      }
    }

  private lazy val TaggedAny: EnvironmentTag[Any] =
    implicitly[EnvironmentTag[Any]]

  implicit private class ZEnvironmentOps[R](private val self: ZEnvironment[R]) extends AnyVal {

    /**
     * Like `ZEnvironment#prune`, but this considers `Any` to be the
     * intersection of zero types - meaning that `.customPrune[Any]` produces an
     * empty `ZEnvironment`.
     *
     * @param tagged
     * @tparam R1
     * @return
     * @see
     *   https://github.com/zio/zio/issues/8481
     */
    def customPrune[R1 >: R](implicit tagged: EnvironmentTag[R1]): ZEnvironment[R1] =
      if (tagged == TaggedAny) ZEnvironment.empty.asInstanceOf[ZEnvironment[R1]]
      else self.prune[R1]
  }
}

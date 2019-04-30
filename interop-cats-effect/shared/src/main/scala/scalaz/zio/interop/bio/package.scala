package scalaz.zio
package interop

import cats.Monad

package object bio {

  implicit def errorful2ImpliesMonad[F[+ _, + _], E](implicit ev: Errorful2[F]): Monad[F[E, ?]] = ev.monad
}

package zio.stream

import zio.ZIO

package object experimental {

  type Pull[-R, +E, +O] = ZIO[R, Option[E], O]
}

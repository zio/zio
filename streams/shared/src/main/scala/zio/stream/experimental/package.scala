package zio.stream

import zio.ZIO

package object experimental {

  type Pull[-R, +E, +I]     = ZIO[R, Option[E], I]
  type Push[-R, +E, -I, +O] = I => Pull[R, E, O]
  type Read[-R, +E, -I, +O] = I => ZIO[R, E, O]
}

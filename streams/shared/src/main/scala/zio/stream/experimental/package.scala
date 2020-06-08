package zio.stream

import zio.ZIO

package object experimental {

  type Pull[-R, +E, +I] = ZIO[R, Option[E], I]
}

package zio.stream

import zio.ZIO

package object encoding2 {

  type Pull[+E, +I] = ZIO[Any, Option[E], I]
}

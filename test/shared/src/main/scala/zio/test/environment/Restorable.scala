package zio.test.environment

import zio.UIO

trait Restorable {
  val save: UIO[UIO[Unit]]
}

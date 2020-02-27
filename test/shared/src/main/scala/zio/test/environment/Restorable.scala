package zio.test.environment

import zio.UIO

trait Restorable extends Serializable {
  val save: UIO[UIO[Unit]]
}

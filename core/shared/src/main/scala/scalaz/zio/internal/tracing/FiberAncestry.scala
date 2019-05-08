package scalaz.zio.internal.tracing

import scalaz.zio.ZTrace

final case class FiberAncestry(parentTrace: Option[ZTrace]) extends AnyVal

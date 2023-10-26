package zio.internal

import zio._

import scala.util.control.NoStackTrace

private[zio] object AsyncJump extends Exception with NoStackTrace

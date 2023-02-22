package zio

import java.util.function.IntFunction

private[zio] trait UpdateRuntimeFlagsWithinPlatformSpecific {
  // By the virtue of using IntFunction, it does not box.
  // Soon available for Scala Native too: https://github.com/scala-native/scala-native/pull/3127
  type RuntimeFlagsFunc[A] = IntFunction[A]
}

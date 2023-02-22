package zio

// This is split because `java.util.function.IntFunction` isn't available in Scala Native yet.
// Once it's available, this should be merged together with the `js-jvm` variant back to `shared`.
// https://github.com/scala-native/scala-native/pull/3127
private[zio] trait UpdateRuntimeFlagsWithinPlatformSpecific {
  type RuntimeFlagsFunc[A] = Int => A
}

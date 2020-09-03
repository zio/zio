package zio.internal

private[internal] abstract class DefaultExecutors {
  final def makeDefault(yieldOpCount: Int): Executor =
    Executor.fromExecutionContext(yieldOpCount)(JSExecutionContext.default)
}

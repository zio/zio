package zio

@FunctionalInterface
trait ZScheduler extends (Runnable => Unit) {
  def apply(task: Runnable): Unit

  def schedule(task: Runnable, delay: Long, unit: TimeUnit): Runnable
}
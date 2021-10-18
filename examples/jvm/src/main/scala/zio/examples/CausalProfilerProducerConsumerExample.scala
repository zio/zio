package zio.examples

import zio._

object CausalProfilerProducerConsumerExample extends ZIOAppDefault {

  val Items         = 1000000
  val QueueSize     = 50
  val ProducerCount = 20
  val ConsumerCount = 5

  def run = {
    val program = Queue.bounded[Unit](QueueSize).flatMap { queue =>
      def producer =
        queue.offer(()).repeatN((Items / ProducerCount) - 1)

      def consumer =
        (queue.take *> CausalProfiler.progressPoint("consumed")).repeatN((Items / ConsumerCount) - 1)

      for {
        producers <- ZIO.forkAll(List.fill(ProducerCount)(producer))
        consumers <- ZIO.forkAll(List.fill(ConsumerCount)(consumer))
        _         <- producers.join *> consumers.join
      } yield ()
    }

    CausalProfiler
      .profile(200) {
        program.forever
      }
      .flatMap(_.writeToFile("profile.coz"))
      .exitCode
  }
}

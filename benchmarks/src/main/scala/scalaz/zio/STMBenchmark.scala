package scalaz.zio
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._
import scalaz.zio.stm.{ STM, TRef }

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(3)
class STMBenchmark {

  private def transfer(receiver: TRef[Int], sender: TRef[Int], much: Int): UIO[Int] =
    STM.atomically {
      for {
        balance <- sender.get
        _       <- STM.check(balance >= much)
        _       <- receiver.update(_ + much)
        _       <- sender.update(_ - much)
        newAmnt <- receiver.get
      } yield newAmnt
    }

  @Benchmark
  def simpleSTM(): Int =
    unsafeRun(for {
      sender    <- TRef.makeCommit(100)
      receiver  <- TRef.makeCommit(0)
      _         <- transfer(receiver, sender, 150).fork
      _         <- sender.update(_ + 100).commit
      _         <- sender.get.filter(_ == 50).commit
    } yield 0)

}

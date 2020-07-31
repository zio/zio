package zio
package stream
package encoding1

import zio.duration._

object ExampleBuffer extends zio.App {

  private val count = 3L

  private val print = ZTransducer.identity[Int].mapM(i => put(i).as(i))
  private val sleep = ZTransducer.identity[Int].mapM(i => clock.sleep(i.seconds).as(i))

  private val print1 = ZTransducer1.tap((i: Int) => put(i))
  private val sleep1 = ZTransducer1.tap((i: Int) => clock.sleep(i.seconds).as(i))
  private val sink1  = ZSink1.collect[Int]

  private def put[A](a: A) =
    console.putStrLn("[%s] %s".format(java.time.Instant.now(), a))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    run0 *> run1.exitCode

  private def run0 =
    ZStream
      .unfold(1)(s => Some(s -> (s + 1)))
      .transduce(print)
      .buffer(2)
      .transduce(sleep)
      .take(count)
      .runCollect
      .zip(ZIO.succeedNow("buffer(2)"))
      .flatMap(put)

  private def run1 =
    ZStream1
      .unfold(1)(s => s -> (s + 1))
      .transduce(print1)
      .transduce(sleep1.buffer(2))
      .take(count)
      .run(sink1)
      .zip(ZIO.succeedNow("encoding1 buffer(2)"))
      .flatMap(put)
}

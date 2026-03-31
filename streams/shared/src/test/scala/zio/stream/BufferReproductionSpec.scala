package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

object BufferReproductionSpec extends ZIOSpecDefault {

  def spec = suite("BufferReproductionSpec")(
    test("buffer(1) pulls exactly 2 elements (1 at consumer, 1 buffered)") {
      for {
        ref <- Ref.make(0)
        stream = ZStream.fromIterable(1 to 10)
          .mapZIO(i => ref.update(_ + 1).as(i))
          .buffer(1)
        
        _ <- stream.take(1).runCollect
        count <- ref.get
      } yield assert(count)(equalTo(2))
    } @@ TestAspect.ignore, // We will run it manually
    
    test("buffer(2) pulls exactly 3 elements") {
      for {
        ref <- Ref.make(0)
        stream = ZStream.fromIterable(1 to 10)
          .mapZIO(i => ref.update(_ + 1).as(i))
          .buffer(2)
        
        _ <- stream.take(1).runCollect
        count <- ref.get
      } yield assert(count)(equalTo(3))
    }
  )

  def main(args: Array[String]): Unit = {
    // Manual runner
    val program = for {
      _ <- Console.printLine("Running buffer(1) test...")
      ref1 <- Ref.make(0)
      _ <- ZStream.fromIterable(1 to 10)
            .mapZIO(i => ref1.update(_ + 1).as(i))
            .buffer(1)
            .take(1)
            .runCollect
      count1 <- ref1.get
      _ <- Console.printLine(s"buffer(1) pulled: $count1 (Expected: 2, Current: 3?)")

      _ <- Console.printLine("Running buffer(2) test...")
      ref2 <- Ref.make(0)
      _ <- ZStream.fromIterable(1 to 10)
            .mapZIO(i => ref2.update(_ + 1).as(i))
            .buffer(2)
            .take(1)
            .runCollect
      count2 <- ref2.get
      _ <- Console.printLine(s"buffer(2) pulled: $count2 (Expected: 3, Current: 4?)")
    } yield ()

    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program).getOrThrowFiberFailure()
    }
  }
}

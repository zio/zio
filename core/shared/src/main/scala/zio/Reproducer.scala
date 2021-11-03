package zio

object Reproducer extends scala.App {

  val n = 1000000

  val runtime = Runtime.default

  var i = 0
  while (i < n) {
    println(i)
    test()
    i += 1
  }

  def test() = {
    val hub: Hub[Int]       = runtime.unsafeRun(Hub.bounded(2))
    val left: Dequeue[Int]  = runtime.unsafeRun(hub.subscribe.reserve.flatMap(_.acquire))
    val right: Dequeue[Int] = runtime.unsafeRun(hub.subscribe.reserve.flatMap(_.acquire))
    var p1                  = 0
    var p2                  = 0
    var p3                  = 0
    var p4                  = 0
    val actor1 =
      runtime.unsafeRun(hub.publish(1).forkDaemon)
    val actor2 =
      runtime.unsafeRun(hub.publish(2).forkDaemon)
    val actor3 =
      runtime.unsafeRun {
        left.take
          .zipWith(left.take) { (first, last) =>
            p1 = first
            p2 = last
          }
          .forkDaemon
      }
    val actor4 = runtime.unsafeRun {
      right.take
        .zipWith(right.take) { (first, last) =>
          p3 = first
          p4 = last
        }
        .forkDaemon
    }
    runtime.unsafeRun {
      for {
        _ <- actor1.join
        _ <- actor2.join
        _ <- actor3.join
        _ <- actor4.join
      } yield assert {
        (p1 == 1 && p2 == 2 && p3 == 1 && p4 == 2) ||
        (p1 == 2 && p2 == 1 && p3 == 2 && p4 == 1)
      }
    }
  }
}

package zio.stm

import zio.TestRuntime
import scala.util.control.Breaks._

class StmIssue1587Spec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "STM made of read only operations".title ^ s2"""
     doen't commit when journal is invalid $getOnlyStmCheck
  """

  def getOnlyStmCheck = {
    val iterations = 1000000
    var badResult  = Option.empty[String]
    breakable {
      for (i <- 1 to iterations) {
        val result = unsafeRun(test)
        if (result != 0 && result != 2) {
          badResult = Some(s"Result[$i/$iterations] $result")
          break()
        }
      }
    }
    badResult aka "badResult" must beNone
  }

  val test = {
    for {
      r0        <- TRef.makeCommit(0)
      r1        <- TRef.makeCommit(0)
      sum1Fiber <- r0.get.flatMap(v0 => r1.get.map(_ + v0)).commit.fork
      _         <- r0.update(_ + 1).flatMap(_ => r1.update(_ + 1)).commit
      sum1      <- sum1Fiber.join
    } yield sum1
  }
}

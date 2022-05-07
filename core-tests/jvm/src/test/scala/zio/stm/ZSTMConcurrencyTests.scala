package zio.stm

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.I_Result
import zio._

object ZSTMConcurrencyTests {

  val runtime = Runtime.default

  /*
   * Tests that no transaction can be committed based on an inconsistent state.
   * We should never observed a case where the transaction fails based on a
   * value that was set by another fiber but never committed.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("0"), expect = Expect.ACCEPTABLE, desc = "permit is released")
    )
  )
  @State
  class ConcurrentGetAndSet {
    val inner: TRef[Boolean]       = runtime.unsafeRun(TRef.makeCommit(false))
    val outer: TRef[TRef[Boolean]] = runtime.unsafeRun(TRef.makeCommit(inner))

    @Actor
    def actor1(): Unit = {
      val stm = for {
        fresh <- TRef.make(false)
        inner <- outer.get
        value <- inner.get
        _ <- if (value) ZSTM.fail("fail")
             else inner.set(true) *> outer.set(fresh)
      } yield value
      val zio = ZIO.foreachPar_(1 to 1000)(_ => stm.commit)
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      r.r1 = 0
  }
}

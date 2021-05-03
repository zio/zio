package zio

import zio.test._
import zio.test.Assertion._

object StateSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("StateSpec")(
      testM("state can be updated") {
        final case class MyState(counter: Int)
        val zio = for {
          _     <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
          count <- ZIO.getStateWith[MyState](_.counter)
        } yield count
        assertM(zio.provideLayer(State.make(MyState(0)).toLayer))(equalTo(1))
      }
    )
}

package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object ZStateSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("StateSpec")(
      testM("state can be updated") {
        final case class MyState(counter: Int)
        val zio = for {
          _     <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
          count <- ZIO.getStateWith[MyState](_.counter)
        } yield count
        assertM(zio.provideLayer(ZState.make(MyState(0)).toLayer))(equalTo(1))
      }
    ) @@ exceptDotty
}

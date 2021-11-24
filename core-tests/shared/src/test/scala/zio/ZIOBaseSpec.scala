package zio

import zio.test._

import scala.annotation.tailrec

trait ZIOBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspect.WithOut[
    Nothing,
    TestEnvironment,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ]] =
    if (TestPlatform.isJVM) Chunk(TestAspect.timeout(120.seconds))
    else Chunk(TestAspect.sequential, TestAspect.timeout(120.seconds))

  sealed trait ZIOTag {
    val value: String
    val subTags: List[ZIOTag] = Nil
  }
  object ZIOTag {
    case object errors extends ZIOTag { override val value = "errors" }
    case object future extends ZIOTag { override val value = "future" }
    case object interop extends ZIOTag {
      override val value                 = "interop"
      override val subTags: List[ZIOTag] = List(future)
    }
    case object interruption extends ZIOTag { override val value = "interruption" }
    case object regression   extends ZIOTag { override val value = "regression"   }
    case object supervision  extends ZIOTag { override val value = "supervision"  }
  }

  def zioTag(zioTag: ZIOTag, zioTags: ZIOTag*): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val tags = zioTags.map(_.value) ++ getSubTags(zioTag) ++ zioTags.flatMap(getSubTags)
    TestAspect.tag(zioTag.value, tags.distinct: _*)
  }

  private def getSubTags(zioTag: ZIOTag): List[String] = {
    @tailrec
    def loop(currentZioTag: ZIOTag, remainingZioTags: List[ZIOTag], result: List[String]): List[String] =
      (currentZioTag.subTags, remainingZioTags) match {
        case (Nil, Nil)      => currentZioTag.value :: result
        case (Nil, t :: ts)  => loop(t, ts, currentZioTag.value :: result)
        case (st :: sts, ts) => loop(st, sts ++ ts, currentZioTag.value :: result)
      }
    zioTag.subTags match {
      case t :: ts => loop(t, ts, Nil)
      case Nil     => Nil
    }
  }

  // TODO Needed?
  /**
   * Builds a spec with a single test.
   */
  override def test[In](label: String)(
    assertion: => In
  )(implicit
    testConstructor: TestConstructor[Nothing, In],
    trace: ZTraceElement
  ): testConstructor.Out =
    zio.test.test(label)(assertion)

  override def suite[In](label: String)(specs: In*)(implicit
    suiteConstructor: SuiteConstructor[In],
    trace: ZTraceElement
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError, suiteConstructor.OutSuccess] =
    zio.test.suite(label)(specs: _*)
}

package zio.interop

import catalysts.Platform
import cats.kernel.laws.discipline.catsLawsIsEqToProp
import cats.{ Eq, Monad }
import org.scalacheck.{ Arbitrary, Prop }
import org.typelevel.discipline.Laws

trait ExtraMonadTests[F[_]] extends Laws {
  def laws: ExtraMonadLaws[F]

  def monadExtras[A: Arbitrary: Eq](
    implicit
    EqFInt: Eq[F[Int]]
  ): RuleSet =
    new RuleSet {
      def name: String                  = "monadExtras"
      def bases: Seq[(String, RuleSet)] = Nil
      def parents: Seq[RuleSet]         = Nil
      def props: Seq[(String, Prop)] =
        if (Platform.isJvm)
          Seq[(String, Prop)]("tailRecM construction stack safety" -> Prop.lzy(laws.tailRecMConstructionStackSafety))
        else Seq.empty
    }
}

object ExtraMonadTests {
  def apply[F[_]: Monad]: ExtraMonadTests[F] =
    new ExtraMonadTests[F] {
      def laws: ExtraMonadLaws[F] = ExtraMonadLaws[F]
    }
}

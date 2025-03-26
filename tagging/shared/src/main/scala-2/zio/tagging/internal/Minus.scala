package zio.tagging.internal

//import scala.language.experimental.macros

trait Minus[R, M] {
  type Out
  def evidence: (M with Out) =:= R
}

object Minus {

  def apply[R, M, O](implicit ev: M with O =:= R): Minus.Aux[R, M, O] = new Minus[R, M] {
    type Out = O
    override val evidence = ev
  }

  type Aux[R, M, O] = Minus[R, M] { type Out = O }

  implicit def materialize[R, M, O]: Minus.Aux[R, M, O] = macro MinusMacros.materialize[R, M, O]
}

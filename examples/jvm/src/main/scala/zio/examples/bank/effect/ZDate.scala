package zio.examples.bank.effect

import java.time.LocalDate

import zio.UIO

trait ZDate {

  val date: ZDate.Effect

}

object ZDate {

  trait Effect {

    def today: UIO[LocalDate]

  }

}

object ZDateLive extends ZDate.Effect {

  override def today: UIO[LocalDate] = UIO.effectTotal(LocalDate.now())

}

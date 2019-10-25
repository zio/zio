package zio.examples.bank.domain

import java.time.LocalDate

case class Account(id: Int, ownerName: String)

case class Operation(
  id: Int,
  valueInCents: Long,
  ownerAccount: Account,
  peerAccountReference: Int,
  transactions: List[Transaction]
)

sealed trait Action

case object Credit extends Action
case object Debit  extends Action

case class Transaction(id: Int, targetAccount: Account, valueInCents: Long, action: Action, processingDate: LocalDate)

case class Balance(valueInCents: Long, account: Account)

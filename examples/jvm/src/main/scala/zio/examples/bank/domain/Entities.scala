package zio.examples.bank.domain

case class Account(id: Int, ownerName: String)

case class Operation(id: Int,
                     valueInCents: Long,
                     ownerAccount: Account,
                     peerAccount: String,
                     transactions: List[Transaction])

sealed trait Action

case object Credit extends Action
case object Debit  extends Action

case class Transaction(id: Int, targetAccount: Account, valueInCents: Long, action: Action)

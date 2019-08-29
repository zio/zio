package zio.examples.bank.domain

case class CreateAccount(ownerName: String)

case class CreateOperation(
  valueInCents: Long,
  ownerReference: Int,
  peerReference: Int,
  transactions: List[CreateTransaction],
  isExternal: Boolean
)

case class CreateTransaction(targetAccount: Account, valueInCents: Long, action: Action)

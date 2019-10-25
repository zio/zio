package zio.examples.bank.pure

import java.time.LocalDate

import zio.examples.bank.domain.{ Account, Balance, CreateOperation, CreateTransaction, Credit, Debit }
import zio.examples.bank.failure._
import zio.test._
import zio.examples.bank.service.pure.OperationPure._
import zio.test.Assertion._
object OperationPureSpec
    extends DefaultRunnableSpec(
      suite("OperationPureSpec")(
        test("A CreateOperation command with the value lesser than 1 must fail") {

          val command =
            CreateOperation(0,
                            0,
                            0,
                            List(CreateTransaction(Account(0, "Nonexistent"), 0, Credit, LocalDate.now())),
                            isExternal = true)

          assert(valueIsValid(command), isLeft(equalTo(OperationInvalidValue(0))))

        },
        test("A CreateOperation command with the value greater than 0 must succeed") {

          val command =
            CreateOperation(100,
                            0,
                            0,
                            List(CreateTransaction(Account(0, "Nonexistent"), 0, Credit, LocalDate.now())),
                            isExternal = true)

          assert(valueIsValid(command), isRight(equalTo(command)))

        },
        test("A CreateOperation command without transactions must fail") {

          val command =
            CreateOperation(100, 0, 0, Nil, isExternal = true)

          assert(numberOfTransactions(command), isLeft(equalTo(OperationWithoutTransactions())))

        },
        test("A CreateOperation command with transactions must succeed") {

          val command =
            CreateOperation(
              100,
              0,
              0,
              List(CreateTransaction(Account(0, "Nonexistent"), 100, Credit, LocalDate.now())),
              isExternal = true
            )

          assert(numberOfTransactions(command), isRight(equalTo(command)))

        },
        test("A CreateOperation command with invalid transactions must fail") {

          val transactions = List(CreateTransaction(Account(0, "Nonexistent"), -100, Credit, LocalDate.now()))
          val command =
            CreateOperation(100, 0, 0, transactions, isExternal = true)

          assert(transactionsAreValid(command), isLeft(equalTo(OperationWithInvalidCreateTransactions(transactions))))

        },
        test("A CreateOperation command with valid transactions must succeed") {

          val command =
            CreateOperation(
              100,
              0,
              0,
              List(CreateTransaction(Account(0, "Nonexistent"), 100, Credit, LocalDate.now())),
              isExternal = true
            )

          assert(transactionsAreValid(command), isRight(equalTo(command)))

        },
        test("A CreateOperation command with invalid transactions value sum must fail") {

          val command =
            CreateOperation(
              100,
              0,
              0,
              List(
                CreateTransaction(Account(0, "Nonexistent"), 150, Debit, LocalDate.now()),
                CreateTransaction(Account(0, "Nonexistent"), 100, Credit, LocalDate.now())
              ),
              isExternal = false
            )

          assert(
            transactionsSumEqualsAmount(command),
            isLeft(equalTo(OperationValueAndSumOfTransactionsDifferent(100, 150)))
          )

        },
        test("A CreateOperation command external with invalid transactions value sum must fail") {

          val command =
            CreateOperation(
              100,
              0,
              0,
              List(
                CreateTransaction(Account(1, "Nonexistent"), 110, Debit, LocalDate.now())
              ),
              isExternal = true
            )

          assert(
            transactionsSumEqualsAmount(command),
            isLeft(equalTo(OperationValueAndSumOfTransactionsDifferent(100, 110)))
          )

        },
        test("A CreateOperation command external with valid transactions value sum must succeed") {

          val command =
            CreateOperation(
              100,
              0,
              0,
              List(
                CreateTransaction(Account(1, "Nonexistent"), 100, Credit, LocalDate.now())
              ),
              isExternal = true
            )

          assert(transactionsSumEqualsAmount(command), isRight(equalTo(command)))

        },
        test("An operation created by a account with insufficient value must fail") {
          assert(
            accountAmountIsEnough(Balance(100, Account(0, "Nonexistent")), 150, false),
            isLeft(equalTo(OperationOwnerAccountInsufficientValue(150, 100)))
          )
        },
        test("An operation created by a account with the needed value must succeed") {
          assert(accountAmountIsEnough(Balance(100, Account(0, "Nonexistent")), 100, false), isRight(equalTo(())))
        }
      )
    )

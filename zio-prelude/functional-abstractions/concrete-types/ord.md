# Ord

> `Ord[A]` describes a total ordering on values of type `A`.

`Ord[A]` describes a total ordering on values of type `A`.

Its signature is:

```scala
trait Equal[-A] {
  def equal(left: A, right: A): Boolean
}

trait Ord[-A] {
  def compare(left: A, right: A): Ordering
  final def equal(left: A, right: A): Boolean =
    compare(left, right) == Ordering.Equals
}

sealed trait Ordering
object Ordering {
  case object LessThan extends Ordering
  case object GreaterThan extends Ordering
  case object Equals extends Ordering
}
```

That is, we can compare any two values of type `A` and either the `left` value is less than the `right`, the `left` value is greater than the `right`, or the two values are equal.

Furthermore, the results of these comparisons must satisfy the properties that we expect of a total ordering. For example, if `a` is greater than `b` and `b` is greater than `c` then `a` must be greater than `c` and if `a` is greater than `b` then `b` must be less than `a`.

The `Ord` abstraction builds on the `Equal` abstraction because being able to define a total ordering on values of a type implies being able to compare values of that type for equality, as shown by our implementation of the `equal` operator in terms of `compare` above. This means that if we define an instance of the `Ord` abstraction for a data type we automatically get an instance of the `Equal` abstraction for free and our definition of equality is guaranteed to be consistent with the ordering.

The `Ord` abstraction is similar to the `Ordering` type class in the Scala standard library and in fact we can convert any `scala.math.Ordering` to an `Ord` using the `fromScala` operator and any `Ord` to a `scala.math.Ordering` using the `toScala` operator.

```scala

val toScala: scala.math.Ordering[Int] =
  Ord[Int].toScala
// toScala: Ordering[Int] = zio.prelude.Ord$$anonfun$toScala$2@3e095343

val fromScala: Ord[Int] =
  Ord.fromScala(scala.math.Ordering[Int])
// fromScala: Ord[Int] = zio.prelude.Ord$$anonfun$fromScala$2@5c921216
```

The main advantages that the `Ord` abstraction provides over the `Ordering` in the Scala standard library is improved type inference due to use of variance and integration with the other functional abstractions in ZIO Prelude.

The `Ordering` type class in the Scala standard library is invariant, meaning that from the perspective of the Scala compiler an `Ordering[A]` and an `Ordering[B]` are completely unrelated, even if `A` is a subtype of `B`. If we look at the signature of the `compare` operator on `Ord` we can see that this does not reflect the natural variance of this type.

An `Ord[A]` is a consumer of `A` values, it takes two `A` values and returns a result describing whether one is less than, greater than, or equal to the other. If we know how to order values of type `A` and `B` is a subtype of `A` then we also know how to order values of type `B`, because every `B` is an `A`!

The version of `Ordering` in the Scala compiler "forgets" this information because it does not use variance, which can lead to type inference problems.

To see this, consider a situation like the following. We have a domain model where customer accounts may be either business accounts or consumer accounts, each of which have a unique identifier.

```scala
sealed trait CustomerAccount

case class BusinessAccount(id: String) extends CustomerAccount
case class ConsumerAccount(id: String) extends CustomerAccount
```

We might define a total ordering for all accounts like this:

```scala

implicit val CustomerAccountOrd: Ord[CustomerAccount] =
  Ord.make {
    case (BusinessAccount(id1), BusinessAccount(id2)) => id1 =?= id2
    case (BusinessAccount(_), ConsumerAccount(_))     => Ordering.LessThan
    case (ConsumerAccount(_), BusinessAccount(_))     => Ordering.GreaterThan
    case (ConsumerAccount(id1), ConsumerAccount(id2)) => id1 =?= id2
  }
// CustomerAccountOrd: Ord[CustomerAccount] = zio.prelude.Ord$$anon$4@1f53457b
```

Basically this says that we are ordering all business accounts before all consumer accounts and then within each account type we are ordering by the identifier. Note that we are using the `=?=` operator which is available on any type for which an `Ord` is defined to return the result of comparing two values of that type.

We can use this ordering to compare two customer accounts

```scala
val customerAccount1: CustomerAccount = BusinessAccount("abc")
// customerAccount1: CustomerAccount = BusinessAccount(id = "abc")
val customerAccount2: CustomerAccount = ConsumerAccount("def")
// customerAccount2: CustomerAccount = ConsumerAccount(id = "def")

val compareCustomerAccounts: Boolean =
  customerAccount1 < customerAccount2
// compareCustomerAccounts: Boolean = true
```

We can also use it to compare two business accounts or two consumer accounts.

```scala
val consumerAccount1: ConsumerAccount = ConsumerAccount("abc")
// consumerAccount1: ConsumerAccount = ConsumerAccount(id = "abc")
val consumerAccount2: ConsumerAccount = ConsumerAccount("def")
// consumerAccount2: ConsumerAccount = ConsumerAccount(id = "def")

val compareConsumerAccounts: Boolean =
  consumerAccount1 < consumerAccount2
// compareConsumerAccounts: Boolean = true
```

This just works because a consumer account is a customer account and we know how to order customer accounts.

Let's see what happens when we try to do the same thing with the `Ordering` type class defined in the Scala standard library.

```scala

sealed trait CustomerAccount

case class BusinessAccount(id: String) extends CustomerAccount
case class ConsumerAccount(id: String) extends CustomerAccount

implicit val CustomerAccountScalaOrdering: scala.math.Ordering[CustomerAccount] =
  new scala.math.Ordering[CustomerAccount] {
    def compare(left: CustomerAccount, right: CustomerAccount): Int =
      (left, right) match {
        case (BusinessAccount(id1), BusinessAccount(id2)) => id1 compare id2
        case (BusinessAccount(_), ConsumerAccount(_))     => -1
        case (ConsumerAccount(_), BusinessAccount(_))     => 1
        case (ConsumerAccount(id1), ConsumerAccount(id2)) => id1 compare id2
      }
}
// CustomerAccountScalaOrdering: Ordering[CustomerAccount] = repl.MdocSession$MdocApp2$$anon$1@7f831d97
```

Besides the lack of specific types in modeling the result of the compare operator this works fine for comparing two customer accounts.

```scala
val customerAccount1: CustomerAccount = BusinessAccount("abc")
// customerAccount1: CustomerAccount = BusinessAccount(id = "abc")
val customerAccount2: CustomerAccount = ConsumerAccount("def")
// customerAccount2: CustomerAccount = ConsumerAccount(id = "def")

val compareCustomerAccounts: Boolean =
  customerAccount1 < customerAccount2
// compareCustomerAccounts: Boolean = true
```

But look what happens when we try to compare two consumer accounts.

```scala
val consumerAccount1: ConsumerAccount = ConsumerAccount("abc")
val consumerAccount2: ConsumerAccount = ConsumerAccount("def")

val compareConsumerAccounts: Boolean =
  consumerAccount1 < consumerAccount2
// error: value < is not a member of MdocApp2.this.ConsumerAccount
//   consumerAccount1 < consumerAccount2
//   ^^^^^^^^^^^^^^^^^^
```

This code doesn't compile! The Scala compiler is unable to compare the consumer accounts because of the lack of variance on `scala.math.Ordering`.

We could widen the type of `ConsumerAccount` to `CustomerAccount` but that is unergonomic. And it wouldn't even work if, for example, we were trying to sort the list and needed to maintain the type information that these were consumer accounts rather than just customer accounts.

The `Ord` abstraction helps us avoid problems like this and integrates seamlessly with the rest of ZIO Prelude.

## Defining Ord Instances

`Ord` instances are already defined for all the types in `ZIO` and the Scala standard library that have a total ordering.

ZIO Prelude is also very good at automatically deriving `Ord` instances for more complex data types like tuples. For example if we have a pair of `Double` values representing `x` and `y` coordinates an ordering is already automatically defined for it.

```scala

Ord[(Double, Double)]
// res5: Ord[(Double, Double)] = zio.prelude.Ord$$anon$5@949fc49
```

The default instance for tuples will order values by the first field and then if the first field is the same by the second field and so on.

If we want to define an ordering for our own data type and potentially include our own ordering logic we can use the `make` operator.

```scala
case class Point(x: Double, y: Double)

object Point {
  implicit val PointOrd: Ord[Point] =
    Ord.make { (left, right) =>
      (left.y =?= right.y) <> (left.x =?= right.x)
    }
}
```

The `<>` operator combines two `Ordering` values, using the result of the first one if it is `GreaterThan` or `LessThan` and otherwise using the result of the second one. You can think of this like sorting by last name and then first name.

So now we are sorting by the `y` coordinate and then the `x` coordinate.

Just like the `Equal` abstraction, the `Ord` abstraction supports a `contramap` operator, so we can define a total ordering on a data type as long as we can transform it into another data type that has a total ordering in a way that preserves information.

This is particularly useful if we want to define an `Ord` instance for our case classes because we can just derive it from the instances for tuples.

```scala
object Point {
  implicit val PointOrd: Ord[Point] =
    Ord[(Double, Double)].contramap(point => (point.y, point.x))
}
```

ZIO Prelude makes it as easy as possible to use and define instances of `Ord` so we can abstract over the property of having a total ordering and integrate it with other parts of ZIO Prelude.

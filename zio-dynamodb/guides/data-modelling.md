# High Level API Data Modelling and Limitations

> The High Level API relies heavily on ZIO Schema and inherits some of it's limitations, namely:

The High Level API relies heavily on ZIO Schema and inherits some of it's limitations, namely:

- A maximum of **22** fields per case class 
- Deep Object-Oriented hierarchies with abstract classes/fields are not supported - only hierarchies one level deep are supported

At first glance these seem limitations seem quite restrictive, however the next sections describe how these can be overcome.

## A maximum of **22** fields per case class
The high level API uses the Reified Optics feature of ZIO Schema to generate optics for case classes and sealed traits. This feature has a limitation of 22 fields per case class. 

This limitation is something to be aware of when designing your models. In practice however this can be overcome by using nested case classes and in the case of deeply nested hierarchies, by using product and sum types (see section below). 

## Deep OO Style hierarchies are not supported - modelling data using Product and Sum Types
Deep Object-Oriented like hierarchies with abstract classes/fields are not supported - only hierarchies one level deep are supported - again this is a limitation of ZIO Schema - however these limitations can be overcome by using product and sum types rather than inheritance.

```scala
object OopStyle {
  // ensure that all concrete classes have id field
  sealed trait Invoice {
    def id: Int
  }
  // (1) Intermediate abstraction with abstract fields - not supported by the ZIO Schema Reified Optics
  sealed trait Billed                                                 extends Invoice {
    def amount: Double
  }
  final case class BilledMonthly(id: Int, amount: Double, month: Int) extends Billed
  final case class BilledYearly(id: Int, amount: Double, year: Int)   extends Billed
  final case class PreBilled(id: Int, count: Int)                     extends Invoice
}

// FP style modelling uses pure data (product and sum types) rather than inheritance hence avoiding classes like (1)
object FPStyle {
  sealed trait BilledBody
  final case class BilledMonthly(month: Int) extends BilledBody
  final case class BilledYearly(year: Int)   extends BilledBody

  sealed trait InvoiceBody
  // (3) extract product refactoring
  final case class Billed(amount: Double, billedBody: BilledBody) extends InvoiceBody
  final case class PreBilled(count: Int)                          extends InvoiceBody

  // (2) extract product refactoring
  final case class Invoice(id: Int, body: InvoiceBody)
}
```

By using the FP approach to modelling we reduce the size of the concrete classes by factoring out common fields into a 
product type. 

For brevity the above examples do not show the full integration with ZIO Schema - [for a full example see this IT test](https://github.com/zio/zio-dynamodb/blob/series/2.x/dynamodb/src/it/scala/zio/dynamodb/TypeSafeApiAlternateModeling.scala).

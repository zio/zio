---
id: design-patterns
title: "Design Patterns"
---

When designing an API, there are patterns that are commonly used. In this section, we are going to talk about some of these patterns.

## Functional Design Patterns

  1. Data Modeling
  2. Declarative Encoding
  3. Executable Encoding

### Data Modeling

Before we start talking about functional data modeling, let's first recap the object-oriented way of modeling data.

The essence of object-oriented data modeling is inheritance. We have classes, traits, abstract classes, and subtyping. The core idea is to describe the commonalities between different types of data in a base interface or abstract class, and then extend it to describe the differences in the subclasses; so each subclass has its type-specific details while sharing the commonalities with the base type:

```scala mdoc:compile-only
abstract class Event {
  def id: String
  def timestamp: Long
}

case class ClickEvent(id: String, timestamp: Long, element: String) extends Event
case class ViewEvent(id: String, timestamp: Long, page: String) extends Event
```

There is some problem with this approach, let's iterate some of them:

One of the problems with this approach is that it is not a good fit when we want to write generic operations on the `Event` type. For example, if we want to write an operation that changes the timestamp of an event, we should match the input event and then do the transformation for each case. Then finally, we should use `asInstanceOf` to cast the result back to the original type:

```scala mdoc:compile-only
def updateTimestamp[E <: Event](event: E, timestamp: Long): E =
  event match {
    case e: ClickEvent => e.copy(timestamp = timestamp).asInstanceOf[E]
    case e: ViewEvent => e.copy(timestamp = timestamp).asInstanceOf[E]
  }
```

This introduces a lot of boilerplate code. It also has a type-safety issue. If we forget to add all the cases for the match expression, the compiler will not be able to detect it.

In functional data modeling, we don't use inheritance. All tools we have are sum types and product types. By using these two types, we can mathematically model the data. Let's try to model the previous example using sum and product types:

```scala
case class Event(id: String, timestamp: Long, details: EventType)

sealed trait EventType
object EventType {
  final case class ClickEvent(element: String) extends EventType
  final case class ViewEvent(page: String) extends EventType
}
```

In this approach, we describe commonalities with product types, and differences with sum types. So we push `id`, `timestamp`, and `details` to the `Event` case class, and encode the type-specific details of the event in the `EventType` which is a sum type.

The product and sum types are called "Algebraic Data Types" (ADT). They are the building blocks of modeling data in functional programming:

- **Product types** are the cartesian product of the types they contain. For example, `Event` is the product of `String`, `Long`, and `EventType`. In scala, we use `case class` to model product types.
- **Sum types** are the disjoint union of the types they represent. For example, `EventType` is the either `ClickEvent` or `ViewEvent`. In scala 2, we use `sealed trait`s and In Scala 3, we use `enum`s to model sum types.

## Design Techniques

  1. Contextual Eliminators
  2. Implicit Traces
  3. Unsafe Markers
  4. Descriptive Errors Using Implicit Evidence
  5. Partial Application of Type Parameters
  6. Double Evaluation Prevention
  7. Method Naming Conventions
  8. Path Dependent Types

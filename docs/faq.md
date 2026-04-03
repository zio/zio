---
id: faq
slug: faq
title: "Frequently Answered Questions (FAQ)"
sidebar_label: "FAQ"
---

In this page we are going to answer general questions related to the ZIO project.

## Where should we encode contextual values like `UserId`, `CorrelationId` in my ZIO application?

1. Should we put `CorrelationId` and `UserId` as well into a `FiberLocal`?
2. Should our effects be something like `val someEffect: ZIO[CorrerlationId & UserId, ErrorType, A]`?
3. Should we keep writing our effects with explicit params as `def someEffect(c: CorrelationId, u: UserId, params...): ZIO[Any, ErrorType, A]`?
4. Should we put these context parameters as implicits, like `def someEffect(params..)(implicit c: CorrelationId, u: UserId): ZIO[Any, ErrorType, A]`?

Before answering these question, make sure you have read the [ZIO Environment Use-cases](reference/contextual/index.md) section.

Now, let's go into this in a little more detail. We have some workflow, `someEffect` that conceptually requires both a `CorrelationId` and a `UserId` to be run. Let's consider any of these solutions in turn.

### Solution 1: Explicit Parameters

The simplest way to model this dependency is just as function parameters:

```scala
def someEffect(userId: UserId, correlationId: CorrelationId, ...): ZIO[Any, ErrorType, A] =
  ???
```

This is a fine starting point. Functions that take arguments are about as simple as you can get. However, there are two main issues with this.

First, it can just become a lot of boilerplate. The signature above looks potentially okay on its own, but if it is being called by some other function which in turn is being called by some other function it can become a lot of boilerplate very fast which is why people turn to all of these alternatives. I don't think there is a hard and fast rule for when it gets to be "too much" boilerplate but if you are unsure I think a good practice can be to use explicit parameters until you "feel the pain" and then you can refactor to one of the alternatives discussed below.

The second problem with this approach is that someEffect is a method and not a first class value, which can limit our ability to work with it in some cases. But typically the boilerplate is the overwhelming problem that leads people to move away from this approach.

### Solution 2: Implicit Parameters

The second alternative you highlight is to make these parameters implicit.

```scala
def someEffect(...)(implicit userId: UserId, correlationId: CorrelationId): ZIO[Any, ErrorType, A] =
  ???
```

This addresses the boilerplate problem with explicit parameters by allowing us to pass them implicitly. We would basically never recommend this solution.

To reason about implicit values in a principled way we want them to be "coherent" which means there is only one implicit value corresponding to any type within our entire program. For example, there is only one Associative instance for String if we are using functional abstractions, or there is only one `JsonEncoder` instance for Person.

If this requirement is not satisfied we get into anti-patterns like an `implicit ExecutionContext`, where changing our imports or moving a block of code can change which thread pool we run on.

By definition, contextual values like this never satisfy this coherence requirement because there are lots of different `CorrelationId` and `UserId` values in our program. We need to pass them around explicitly or implicitly precisely because there are different ones.

So while there are some cases where there might be different alternatives we want to consider this one I think we can rule out.

### Solution 3 and 4: Environment and FiberRefs

The final two alternatives are modeling these contextual values as part of the ZIO Environment or as `FiberRef` values.

If we model both of these requirements as part of the environment our method signature would look like this:

```scala
def someEffect(...): ZIO[UserId & CorrelationId, ErrorType, A] =
  ???
```

If we model them as `FiberRef` values we would define `FiberRef` values that described both the `UserId` and the `CorrelationId`:

```scala
val currentUserId: FiberRef[UserId] = ???
val currentCorrelationId: FiberRef[CorrelationId] = ???

def someEffect(...): ZIO[Any, ErrorType, A] =
  ???
```

Both of these approaches are similar in that they allow us to avoid the boilerplate associated with passing around the `UserId` and the `CorrelationId`, they allow us to treat `someEffect` as a value, and they allow us to locally modify the current value of the `UserId` and `CorrelationId`.

The main difference between these two approaches is that with the ZIO Environment we reflect the fact that `someEffect` needs a `CorrelationId` and a `UserId` in the type signature, whereas when we use a `FiberRef` this requirement is not reflected in the type signature.

Including these requirements in our type signature can be both an advantage and a disadvantage. The advantage is that we make explicit that `someEffect` requires this contextual information, and we cannot even run `someEffect` without providing it. The disadvantage is that we have to include these requirements in the type signatures of all of our workflows, which can "bubble up" through many method signatures and arguably exposes an implementation detail.

So the question I would ask here, going back to our original answer, is whether including these requirements in your type signature is helpful for you to reason about your program. Some related questions you might ask yourself are "Would it make sense to run the workflow if a requirement were not provided?" and "Is there a sensible default value of this requirement"?

Applying these to the CorrelationId and UserId we would be tempted to not include the `CorrelationId` in the environment. It may depend what we are doing with it but it seems like the `CorrelationId` is a low level implementation detail associated with logging that we do not need cluttering up our method signatures. There seems to be a very sensible default `CorrelationId` of None indicating that there is no `CorrelationId` associated with whatever we are doing and we can still run our program without having a `CorrelationId`, our logs will just not be as helpful as they otherwise would be which we can see and correct.

On the other hand for `UserId` we could easily see coming to the opposite conclusion. If `UserId` is supposed to tell us which user we are supposed to look up in a database or whether we are supposed to be able to look up certain information at all then we may not even be able to run `someEffect` without having a `UserId`. Of course we could just fail at runtime but failing at runtime is much more severe than just logging less precisely and normally we want to use the type system to convert runtime failures to compile time failures. So this seems like it might be a great case for using the ZIO environment.

## In ZIO ecosystem, there are lots of data types which they have `Z` prefix in their names. What this prefix stands for? Does it mean, that data type is effectual?

No, it doesn't denote that the data type is effectual. Instead, the `Z` prefix is used for two purposes:

1. **Polymorphic Version of Another Data Type** — The `Z` prefix indicates a more polymorphic version of another data type, not a data type that is effectual. So for example `IO` and `ZIO` are equally effectual but `ZIO` is more polymorphic because it has the additional type parameter `R`.

2. **Term Disambiguation** — There are some cases where the `Z` prefix is used to disambiguate a term that might otherwise be too common and create risk of name conflicts (e.g. `ZPool`).

This convention is true across all ZIO ecosystem. For example, in ZIO Prelude, the `ZValidation` is a more general version of `Validation` that is polymorphic in the log type. `ZSet` is a more polymorphic version of a _Set_ that is polymorphic in the measure type. `ZPure` is more polymorphic than its type aliases in several ways as represented by its different type parameters and also serves to disambiguate it as _Pure_ which is too general.

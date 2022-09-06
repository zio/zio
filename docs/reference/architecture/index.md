---
id: introduction 
title: "Introduction to The ZIO Application Architecture"
---

Defining services in ZIO is not very different from object-oriented style, it has the same principle: coding to an interface, not an implementation. Therefore, ZIO encourages us to implement this principle by using _Service Pattern_, which is quite similar to the object-oriented style.

Before diving into writing services in ZIO style, let's review how we define them in an object-oriented fashion in the next section.

## Defining Services in OOP

Here are the steps we take to implement a service in object-oriented programming:

1. **Service Definition**— In object-oriented programming, we define services with traits. A service is a bundle of related functionality that is defined in a trait:

```scala mdoc:silent:nest
trait FooService {

}
```

2. **Service Implementation**— We implement these services by using classes:

```scala mdoc:silent:nest
class FooServiceImpl extends FooService {
    
}
```

3. **Defining Dependencies**— If the creation of a service depends on other services, we can define these dependencies by using constructors:

```scala mdoc:silent:nest
trait ServiceA {

}

trait ServiceB {

}

class FooServiceImpl(a: ServiceA, b: ServiceB) extends FooService {

}
```

In object-oriented programming, the best practice is to _program to an interface, not an implementation_. So in the previous example, `ServiceA` and `ServiceB` are interfaces, not concrete classes.

4. **Injecting Dependencies**— Now, the client of `FooServiceImpl` service can provide its own implementation of `ServiceA` and `ServiceB`, and inject them to the `FooServiceImpl` constructor:

```scala mdoc:silent:nest
class ServiceAImpl extends ServiceA
class ServiceBImpl extends ServiceB
val fooService = new FooServiceImpl(new ServiceAImpl, new ServiceBImpl)
```

Sometimes, as the number of dependent services grows and the dependency graph of our application becomes complicated, we need an automatic way of wiring and providing dependencies into the services of our application. In these situations, we might use a dependency injection framework to do all its magic machinery for us.


## Defining Services in ZIO

A service is a group of functions that deals with only one concern. Keeping the scope of each service limited to a single responsibility improves our ability to understand code, in that we need to focus only on one topic at a time without juggling too many concepts together in our head.

In functional Scala as well as in object-oriented programming the best practice is to _Program to an Interface, Not an Implementation_. This is the most important design principle in software development and helps us to write maintainable code by:

* Allowing the client to hold an **interface as a contract** and don't worry about the implementation. The interface signature determines all operations that should be done.

* Enabling a developer to **write more testable programs**. When we write a test for our business logic we don't have to run and interact with real services like databases which makes our test run very slow. If our code is correct our test code should always pass, there should be no hidden variables or depend on outside sources. We can't know that the database is always running correctly. We don't want to fail our tests because of the failure of external service.

* Providing the ability to **write more modular applications**. So we can plug in different implementations for different purposes without a major modification.

It is not mandatory, but ZIO encourages us to follow this principle by bundling related functionality as an interface by using the _Service Pattern_.

The core idea is that a layer depends upon the interfaces exposed by the layers immediately below itself, but is completely unaware of its dependencies' internal implementations.

In object-oriented programming:

- **Service Definition** is done by using _interfaces_ (Scala trait or Java Interface).
- **Service Implementation** is done by implementing interfaces using _classes_ or creating _new object_ of the interface.
- **Defining Dependencies** is done by using _constructors_. They allow us to build classes, given their dependencies. This is called constructor-based dependency injection.

We have a similar analogy in the Service Pattern, except instead of using _constructors_ we use **`ZLayer`** to define dependencies. So in ZIO fashion, we can think of `ZLayer` as a service constructor.

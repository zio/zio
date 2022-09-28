---
id: programming-paradigms-in-zio
title: "Programming Paradigms in ZIO"
---

It is important to realize that the programming paradigm used to write a software system has a significant impact on its design and architecture. In this section, we are going to talk the foundation of ZIO from the programming paradigm perspective:

- Functional and object-oriented programming
- Declarative and imperative programming
- Structured Programming
- Aspect-oriented Programming

## Functional and Object-Oriented Programming

Every computer program is written in the form of a set of operations and data structures:

- A data are nouns in the program, such as `Person`, `Address`, `Order`, etc. They represent a piece of information, configuration, or state that is used by operations.
- Operations are verbs, such as `createOrder`, `updateOrder`, `deleteOrder`, and etc. They are methods or functions that operate on data.

The way we organize these two elements in our program determines the programming paradigm we use; object-oriented programming (OOP) or functional programming (FP).

### Object-Oriented Programming

In object-oriented programming, we organize our program by bundling related data and operations into a single unit called an object. Each object has its own state and behavior. This is the fundamental construct of object-oriented programming. All other constructs like classes, interfaces, inheritances, subtyping are built around this concept.

Therefore, the object is the only option we have in object-oriented programming to organize our programs. We have only one hammer for all classes of design problems.

So, we can say that the most important result of object-oriented programming is modularity. We can package related data and operations into a single unit and reuse them in other parts of our program.

#### Classes

In statical-typed object-oriented programming, we can define a "category" of objects by using a class. A class is a blueprint that defines the structure of all objects in that category. A class is basically define the whole class of objects that all have similarities.

We can create an instance of a class by using the `new` keyword. This instance is called an object.

#### Subtyping

Another great feature of object-oriented programming is sub-typing. We can define a new class that inherits from an existing class. This new class is called a sub-class or a child class, and the existing class is called a super-class or a parent class. Using sub-typing, we can define a whole class of objects and then classify it into sub-classes.

#### Interfaces and Polymorphism

In object-oriented programming, we can also define an interface. An interface is a contract that defines the behavior that is shared by all classes that implement that interface.

Using interfaces, we can achieve polymorphism when writing services. For example, we can define a `Logger` interface that defines the `log` method. Then we can define a `ConsoleLogger`, `FileLogger`, or `JsonLogger` that all implement the same `Logger` interface. This way, we can use the same `Logger` interface to inject different implementations of the `Logger` interface into our services.

### Functional Programming

In the previous section, we discussed object-oriented programming and saw that the object is the basis of object-oriented programming. Let's talk about functional programming now and see what its basis is.

A functional program is modeled as a set of mathematical functions. By mathematical functions, we mean those that take an immutable input and produce an immutable output while having referential transparency.

Functions are the basis of FP, and the basis of functions is the lambda. Lambdas are functions that can be passed as arguments to other functions or returned as results. So we can say that the lambda is a fancy term for first-class functions (functions as values).

In contrast to object-oriented programming, functional programming separates data and operations into two different worlds. Data is immutable, and operations are pure functions. This separation of data and operations is the fundamental philosophy of functional programming.

In FP, we have only two building blocks to model our programs:

- Data (Algebraic data types)
- Operations (Functions)

We describe our data (input and output) using constructs called "algebraic data types" which for Scala programmers means _sealed traits_ (or enums) and ـcase classesـ. So there is two building blocks for describing data in FP. To describe operations, we have functions.

Separation of data and operations is the fundamental concept of functional programming. In contrast to object-oriented programming, we have no tools for abstraction, modularity, interfaces, and polymorphism.

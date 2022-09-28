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

### Embracing Both Functional and Object-Oriented Programming

ZIO is a functional programming library which also brings the power of object-oriented programming into the functional world. It tries to combine the best of both worlds.

We use FP to achieve **code maintainability** and OOP to achieve **code organization**:

1. Functional programming gives us the following tools in terms of **code maintainability**:

    - **Data Modeling** using Algebraic Data Types
    - **Functional Design** using functions to create Domain Specific Languages (DSLs)
    - **Composability** using Pure and Referentially Transparent Functions

2. Object-oriented programming gives us the following tools in terms of **code organization**:

    - **Methods** which help us to operate on a specific data attached to an object
    - **Constructors** which help us to create a new instance of a data type
    - **Modules** which allows us to bundle together related operations into a single unit

So, we leverage the power of both FP and OOP to build a better software system in ZIO.

## Imperative and Declarative Programming

Another important aspect of programming paradigms is the difference between imperative and declarative programming.

In imperative programming, we describe the steps ("How") the computer should take to solve a problem. In declarative programming, we describe the problem itself ("What") and let the computer figure out the steps to solve it.

Although Scala supports both imperative and declarative programming styles, ZIO uses a declarative programming style. The ZIO runtime interprets the program as a set of effects and decides what steps to take to execute it.

## Structured Programming

The idea of structured concurrency is based on the structured programming paradigm. So, let's talk about structured programming first.

In the early days of programming, we used to write programs in a linear manner. Program were composed of a series of instructions that executed one by one. Using goto statements, we could jump to any part of the program and change its execution flow. This was the first programming paradigm called procedural programming. Writing programs in such a linear fashion was error-prone and hard to maintain. Such a program was also complicated to read, understand, and reason about.

Structured programming paradigms were introduced to solve this problem. Structured programming uses control structures like "if-then-else" to make the program flow more logical. Without these control structures, we cannot jump to any part of the program.

In structured programming, we use control structures to organize our code into blocks. These blocks are called "structured blocks" and are the building blocks of structured programming.

A structured control flow makes nested blocks of code with clear boundaries. Each new block of code has its own scope where all objects defined in that block are only visible inside that block. As a result, objects are bound to their enclosing blocks for their lifetime. Having clear scopes and lifetimes of objects make it easier to understand the control flow of the program.

ZIO embraces the structured programming into the next level by using this paradigm in other areas of programming such as [structured concurrency](../fiber/index.md#structured-concurrency), [scope based resource management](../resource/scope.md), and also regional interruption model.

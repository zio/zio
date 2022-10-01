---
id: architecture-of-a-zio-application
title: "Architecture of a ZIO Application"
---

## Introduction

Designing and architecting a software system is a complex task. We should consider both the functional and non-functional requirements of the system.

The functional requirements are the features of the system which are directly related to the business domain and its problems. They are the core of the system and the main reason why we are designing and building the application.

Non-functional requirements are characteristics of the system that are used to qualify it in terms of "what should the system be" rather than "what should the system do," e.g.:

  1. Correctness
  2. Testability
  3. Maintainability
  4. Low Latency
  5. High Throughput
  6. Robustness
  7. Resiliency
  8. Efficiency
  9. Developer Productivity
  10. Scalability
  11. Monitoring
  12. Configurability

In this article, from the perspective of application architecture, we are going to look at some design elements that we can apply to our ZIO applications to make them more ergonomic and maintainable.

## Correctness

Correctness is the ability of a system to do what it is supposed to do. ZIO provides us correctness property through local reasoning because of referential transparency and its type-safety.

When we have referential transparency, we do not need to look at the whole program to understand the behavior of a piece of code. We can reason about the application behavior locally and then make sure that all components work together correctly, from button to top.

The type system of ZIO also prevents us to introduce common bugs at runtime. Here are two examples:

  1. **Resource Management**— When we have a ZIO effect that has a type of `ZIO[Scope, IOException, FileInputStream]`, we can be sure that this effect will open a resource, and we should care about closing it. So then by using `ZIO.scoped(effect)` we can be sure that the resource will be closed after the effect is executed and the type of effect will be changed to `ZIO[Any, IOException, FileInputStream]`. To learn more about `ZIO.scoped` and resource management using `Scope`, please refer to the [Scope](../resource/scope.md) of the [resource management](../resource/index.md).

  2. **Error Management**— In ZIO errors are typed, so we can describe all possible errors that can happen in our effect. And from the correctness perspective, the type system helps us to be sure we have handled all errors or not. For example, if we have an effect of type `ZIO[Any, IOException, FileInputStream]`, by looking at the effect type, we can be sure the effect is exceptional, and we should handle its error. To learn more about error management in ZIO, please refer to the [error management](../error-management/index.md) section.

## Testability

ZIO has a strong focus on testability which supports:

  1. Property-based Checking
  2. Testing Effectful and Asynchronous Codes
  3. Testing Passages of Time
  4. Sharing Layers Between Specs
  5. Resource Management While Testing
  6. Dynamic Test Generation
  7. Test Aspects (AOP)
  8. Non-flaky Tests

To learn more about testing in ZIO, please refer to the [testing](../test/index.md) section.

----------

1. API Design Patterns

   1. Data Modeling
   2. Contextual Eliminator
   3. Implicit Traces
   4. Unsafe Marker
   5. Descriptive Errors Using Implicit Evidence
   6. Partial Application of Type Parameters
   7. Double Evaluation Prevention
   8. Smart Constructors

2. Architectural Patterns

   1. Dependency Injection
   2. Service Pattern
   3. Onion Architecture
   4. Sidecar Pattern
   5. Composable ZIO Applications
   6. Mixed Applications
   7. Streaming Architecture

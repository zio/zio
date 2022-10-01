---
id: architecture-of-a-zio-application
title: "Architecture of a ZIO Application"
---

## Introduction

Designing and architecting a software system is a complex task. We should consider both the functional and non-functional requirements of the system.

The functional requirements are the features of the system which are directly related to the business domain and its problems. They are the core of the system and the main reason why we are designing and building the application.

Non-functional requirements are characteristics of the system that are used to qualify it in terms of "what should the system be" rather than "what should the system do," e.g.:

  1. Maintainability
  2. Low Latency
  3. High Throughput
  4. Robustness
  5. Resiliency
  6. Correctness
  7. Efficiency
  8. Developer Productivity
  9. Scalability
  10. Monitoring
  11. Configurability
  12. Testability

In this article, from the perspective of application architecture, we are going to look at some design elements that we can apply to our ZIO applications to make them more ergonomic and maintainable.





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

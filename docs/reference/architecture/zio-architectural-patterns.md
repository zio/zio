---
id: zio-architectural-patterns
title: "ZIO Architectural Patterns"
---

In this section, we are going to talk about the design elements of a ZIO application and the ZIO idiomatic way of structuring codes to write ZIO applications.

## Onion Architecture

Onion architecture is a software architecture pattern that is used to create loosely coupled, maintainable, and testable applications by layering the application into a set of concentric circles.

- The innermost layer contains the domain model. Its language has the highest level of abstraction.
- From the very center, the language domain model is surrounded successively by other layers, each of which is more technical and has a lower level of abstraction than the previous one.
- The outermost layer contains the final language is the one that is closest to the environment in which the application is running. For example, the outermost layer could be the user interface, a web API, etc.

Onion architecture is based on the _inversion of control_ principle. So each layer is dependent on the underlying layer, but not on the layers above it. This means that the innermost layer is independent of any other layers.

In ZIO by taking advantage of both functional and object-oriented programming, we can implement onion architecture in a very simple and elegant way. To implement this architecture, please refer to the [Writing ZIO Services](../architecture/index.md) section which empowers you to create layers (services) in the onion architecture. In order to assemble all layers and make the whole application work, please refer to the [Dependency Injection In ZIO](../di/index.md) section.

---
id: zrefm
title: "ZRefM"
---

A `ZRefM[RA, RB, EA, EB, A, B]` is a polymorphic, purely functional description of a mutable reference. 

The fundamental operations of a `ZRefM`are `set` and `get`. 
- **`set`** takes a value of type `A` and sets the reference to a new value, requiring an environment of type `RA` and potentially failing with an error of type `EA`. 
- **`get`** gets the current value of the reference and returns a value of type `B`, requiring an environment of type
`RB` and potentially failing with an error of type `EB`.

When the error and value types of the `ZRefM` are unified, that is, it is a `ZRefM[E, E, A, A]`, the `ZRefM` also supports atomic `modify` and `update` operations.


> _**Note:**_
>
> Unlike `ZRef`, `ZRefM` allows performing effects within update operations, at some cost to performance. Writes will semantically block other writers, while multiple readers can read simultaneously.

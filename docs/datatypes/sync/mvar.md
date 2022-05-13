---
id: mvar 
title: "MVar"
---

An `MVar[A]` is a mutable location that is either empty or contains a value of type `A`. It has two fundamental operations: 
- `put` which fills an `MVar` if it is empty and blocks otherwise.
- `take` which empties an `MVar` if it is full and blocks otherwise. 

They can be used in multiple different ways:
- As synchronized mutable variables
- As channels, with `take` and `put` as `receive` and `send`
- As a binary semaphore `MVar[Unit]`, with `take` and `put` as `wait` and `signal`

They were introduced in the paper [Concurrent Haskell](#http://research.microsoft.com/~simonpj/papers/concurrent-haskell.ps.gz) by Simon Peyton Jones, Andrew Gordon and Sigbjorn Finne.

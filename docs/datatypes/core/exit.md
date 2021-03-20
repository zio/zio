---
id: exit
title: "Exit"
---

An `Exit[E, A]` value describes how fibers end life. It has two possible values:
- `Exit.Success` contain a success value of type `A`. 
- `Exit.Failure` contains a failure [Cause](cause.md) of type `E`.


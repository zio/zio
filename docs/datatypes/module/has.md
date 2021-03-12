---
id: has
title: "Has"
---
The trait `Has[A]` is used with ZIO environment to express an effect's dependency on a service of type `A`. <br>
For example,`RIO[Has[Console.Service], Unit]` is an effect that requires a `Console.Service` service
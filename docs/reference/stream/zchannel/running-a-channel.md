---
id: running-a-channel
title: "Running a Channel"
---

To run a channel, we can use the `ZChannel.runXYZ` methods:

- `ZChannel#run`— The `run` method is the simplest way to run a channel. It only runs a channel that doesn't read any input or write any output.
- `ZChannel#runCollect`— It will run a channel and collects the output and finally returns it along with the done value of the channel.
- `ZChannel#runDrain`— It will run a channel and ignore any emitted output.
- `ZChannel#runScoped`— Using this method, we can run a channel in a scope. So all the finalizers of the scope will be run before the channel is closed.

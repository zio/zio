---
id: configurable-zio-application
title: "Tutorial: How to make a ZIO application configurable?"
sidebar_label: "Making a ZIO application configurable"
---

## Introduction

One of the most common requirements for writing an application is to be able to configure it, specially when we are writing cloud-native applications.

In this tutorial, we will start with a simple ZIO application and then try to make it configurable using the ZIO Config library.

## Prerequisites

We will use the [ZIO Quickstart: Restful Web Service](../quickstarts/restful-webservice.md) as our ground project. So make sure you have downloaded and tested it before you start this tutorial.

## Problem

We have a web service that does not allow us to configure the host and port of the service:

```scala
git clone git@github.com:khajavi/zio-quickstart-restful-webservice.git
cd zio-quickstart-restful-webservice
sbt run
```

The output is:

```
Server started on http://localhost:8080
```

We want to be able to configure the host and port of the service, so that before running the application, we specify the host and port of the service.

## Solution

First, we need to add the zio-config library to our project.

```

```
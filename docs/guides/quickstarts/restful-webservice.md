---
id: restful-webservice
title: "ZIO Quickstart: Building RESTful Web Service"
sidebar_label: "RESTful Web Service"
---

This quickstart shows how to build a RESTful web service using ZIO. It uses
- [ZIO HTTP](https://dream11.github.io/zio-http/) for the HTTP server
- [ZIO JSON](https://zio.github.io/zio-json/) for the JSON serialization
- [ZIO Logging](https://zio.github.io/zio-logging/) for integrate logging with slf4j
- [ZIO Config](https://zio.github.io/zio-config/) for loading configuration data

## Running The Example

First, open the console and clone the project using `git` (or you can simply download the project) and then change the directory:

```scala
git clone git@github.com:khajavi/zio-quickstart-restful-webservice.git 
cd zio-quickstart-restful-webservice
```

Once you are inside the project directory, run the application:

```bash
sbt run
```

## Explanation


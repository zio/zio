# Testing ZIO DynamoDB Applications

> The recommendation is to use full-stack integration tests of your ZIO DynamoDB repository code against a "local" DynamoDB 
instance of which there are two choices:

### Full Stack Integration Testing

The recommendation is to use full-stack integration tests of your ZIO DynamoDB repository code against a "local" DynamoDB 
instance of which there are two choices:

- [DynamoDB Local JAR](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) - a stand-alone 
JAR file that can be run on your local machine, also is available as a docker image.
- [Docker LocalStack](https://docs.docker.com/guides/localstack/) - a docker image that provides a local AWS cloud stack including DynamoDB

You should use whichever is most convenient for your development environment. The ZIO DynamoDB library itself uses DynamoDB Local JAR 
as a docker image as the full functionality of LocalStack is not required. For examples of how to use DynamoDB Local 
please see the many ZIO DynamoDB integration tests.   

### Unit Testing Database Mapping using the ZIO DynamoDB JSON Module

There is also an opportunity to unit test Database mappings using the optional `zio-dynamodb-json` module. 
This module provides a way to render a case class to a JSON string that represents the native DynamoDB types, including
any discriminators for sum types - please see the [ZIO DynamoDB JSON](../reference/zio-dynamodb-json) section for more details.

# How to Deploy to AWS Lambda

> ZIO Lambda functions run on AWS Lambda through a custom runtime. You package the function as a GraalVM native image, which starts quickly and needs no JVM, and then deploy it either as a zip file or as a container image. This page covers both paths.

Lambda layers, which put the ZIO Lambda runtime in a separate package, are not covered here yet.

## Building the Native Image

To build the native binary of the example project, run this command from the root of the zio-lambda repository:

```shell
sbt "zio-lambda-example/GraalVMNativeImage/packageBin"
```

The build writes the binary to `lambda-example/target/graalvm-native-image/zio-lambda-example`. The example project sets `containerBuildImage` to `ghcr.io/graalvm/native-image-community:21.0.2`, so the build runs `native-image` inside Docker. To package your own handler, copy the GraalVM settings of the `zioLambdaExample` project in `build.sbt`, and set `GraalVMNativeImage / mainClass` to your `ZIOAppDefault` object.

## Deploying a Native Binary as a Zip File

Lambda reads its custom runtime from a `bootstrap` file at the root of the zip. The binary is your program, and `bootstrap` starts it.

To create the `bootstrap` file, write a script that runs your binary and place it next to the binary:

```bash
#!/usr/bin/env bash

set -euo pipefail

./zio-lambda-example
```

Make both files executable, then zip them together:

```shell
chmod +x bootstrap zio-lambda-example
zip upload.zip bootstrap zio-lambda-example
```

To create the function, open the AWS Lambda console and choose **Author from scratch**. Under **Runtime**, choose **Amazon Linux 2023** (`provided.al2023`). Under **Code source**, upload `upload.zip`. Then open the **Test** tab, create an event with a JSON body that matches your handler's event type, and run it. The response shows the JSON that your handler returns.

## Deploying a Native Binary in a Container Image

Lambda also runs container images. The image contains the same native binary, and Lambda starts it as the container's command, so it needs no `bootstrap` file.

Create a `Dockerfile` in the root of the repository:

```Dockerfile
FROM gcr.io/distroless/base-debian12
COPY lambda-example/target/graalvm-native-image/zio-lambda-example /app/zio-lambda-example
CMD ["/app/zio-lambda-example"]
```

Adjust the `COPY` path to match your project. Build the image and tag it for your Amazon ECR repository:

```shell
docker build -t native-image-binary .
```

Log in to Amazon ECR, then push the image. Replace the placeholders with your own registry and tag:

```shell
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <your_AWS_ECR_REPO>
docker tag native-image-binary <your-particular-ecr-image-repository>:<your-tag>
docker push <your-particular-ecr-image-repository>:<your-tag>
```

In the AWS Lambda console, choose **Author from scratch**, then choose **Container image** as the deployment type. Select the image you pushed. Because the binary runs inside a container, it adds some start-up overhead compared with the zip deployment.

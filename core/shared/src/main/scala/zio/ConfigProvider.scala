/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

trait ConfigProvider { self =>
  def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A]

  final def orElse(that: ConfigProvider): ConfigProvider =
    new ConfigProvider {
      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        self.load(config).orElse(that.load(config))
    }
}
object ConfigProvider {
  val ConsoleProviderLive: ConfigProvider =
    new ConfigProvider {
      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        ???
    }

  val EnvProviderLive: ConfigProvider =
    new ConfigProvider {
      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        ???
    }

  val PropsProviderLive: ConfigProvider =
    new ConfigProvider {
      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        ???
    }

  val ConfigProviderLive: ConfigProvider =
    EnvProviderLive.orElse(PropsProviderLive)

  val env: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(EnvProviderLive)

  val console: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(ConsoleProviderLive)

  val props: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(PropsProviderLive)

  val tag: Tag[ConfigProvider] = Tag[ConfigProvider]
}

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

/**
 * A ConfigProvider is a service that provides configuration given a description
 * of the structure of that configuration.
 */
trait ConfigProvider { self =>

  /**
   * Loads the specified configuration, or fails with a config error.
   */
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

  /**
   * A config provider layer that loads configuration from interactive console
   * prompts, using the default Console service.
   */
  val console: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(ConsoleProviderLive)

  /**
   * A config provider layer that loads configuration from environment
   * variables, using the default System service.
   */
  val env: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(EnvProviderLive)

  /**
   * A config provider layer that loads configuration from system properties,
   * using the default System service.
   */
  val props: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(PropsProviderLive)

  val tag: Tag[ConfigProvider] = Tag[ConfigProvider]
}

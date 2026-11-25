# Authorization with OAuth2

> `Options.oauth2` lets a command-line application act on behalf of a user of a web service such as GitHub. It adds OAuth2 authorization to a command: the first run asks the user to approve the application in a browser, and every run gives the command an access token for the service's API. The types live in the `zio.cli.oauth2` package.

ZIO CLI obtains the token with the OAuth 2.0 Device Authorization Grant, defined in [RFC 8628](https://datatracker.ietf.org/doc/html/rfc8628). The program prints a web address and a short code, the user opens the address in any browser and enters the code, and the program polls the service until the user approves. The token is saved in a file, so later runs do not ask again. ZIO CLI does not call the service's API for you: your command sends the token with its own requests.

OAuth2 support exists only on the JVM. [How to log users in with OAuth2](how-to-oauth2-login.md) walks through adding a login to an application and writing a provider for another service.

## Adding OAuth2 to a Command

`Options.oauth2` takes the provider of the service and the list of scopes to request, and returns an `Options[OAuth2Token]`:

```scala
object Options {
  def oauth2(provider: OAuth2Provider, scope: List[String]): Options[OAuth2Token]
}
```

The result combines with other options like any `Options` value, as described in [Options](options.md). We require GitHub authorization with the `read:user` scope in a `stars` command that also takes a flag:

```scala
import zio.cli._
import zio.cli.oauth2._

val clientId = "Iv1.0123456789abcdef"

val github: Options[OAuth2Token] = Options.oauth2(OAuth2Provider.Github(clientId), List("read:user"))

val stars: Command[(OAuth2Token, Boolean)] = Command("stars", github ++ Options.boolean("verbose"))
```

`Options.oauth2` adds one option, `--oauth2-file <file>`, which sets the file that stores the token. It is optional. It also adds a section about the authorization to the help output. For an application named `gh-stars`, `--help` prints the following after the banner, on a machine where the home directory is `/home/user`:

```text
gh-stars 1.0.0 -- Lists your starred repositories

USAGE

  $ gh-stars [--oauth2-file <file>]

OPTIONS

  --oauth2-file <file>
    A file.

    File in which OAuth2 access token will be stored.

    This setting is optional. Default: '/home/user/oauth2_github_Iv1.0123456789abcdef_access_token.json'.

3RD PARTY AUTHORIZATION

  This application requires 3rd party authorization (using OAuth2 protocol)
  provided by Github. When the application is launched for the first time,
  instructions to perform the authorization will be displayed. Subsequent launches
  do not require any action unless the access has been revoked.

  Behavior of 3rd party authorization can be modified by options starting with '--oauth2-'.
```

## When Authorization Happens

The token is obtained while the command line is parsed, before your command's function runs. Parsing the OAuth2 option does the following:

1. It reads the token file. If the file holds a token that has not expired, the command gets that token.
2. If the stored token has expired, it is refreshed. The refresh uses the refresh token when the service issued one, and otherwise runs the authorization again.
3. If the file is missing or cannot be decoded, the authorization runs.
4. A new or refreshed token is written to the file.

The built-in options `--help` and the shell-completion options are handled before the command's own options, so they never start an authorization. `--wizard` starts it only when it runs the command line that the wizard built.

When the authorization fails, for example because the user denies access, the code expires or the service returns an error, the error message becomes a `ValidationError` of type `InvalidValue`. The `CliApp` prints the message and fails with `CliError.Parsing`, the same way as for invalid input. [Validation and Errors](validation-and-errors.md) describes these types.

## The Device Authorization Flow

When the authorization runs, ZIO CLI talks to the service in three steps:

```text
CLI                                   Service                       User's browser
 |-- authorization request ------------>|                                 |
 |<-- device code, user code, URL ------|                                 |
 |   prints URL and user code, "Waiting..."                               |
 |                                      |<-- user opens URL, enters code -|
 |-- access token request (polling) --->|                                 |
 |<-- authorization_pending ... token --|                                 |
 |   saves the token to the file                                          |
```

The first request asks the service for a device code, a user code and a verification address. The program prints the address and the user code in a box, with the number of minutes until the code expires, followed by `Waiting...`.

The program then sends the access token request repeatedly. It waits the interval given by the service between requests, or 5 seconds when the service gives none. A `slow_down` answer makes every later wait longer by the interval in the answer, or by 5 seconds. Polling stops when the service returns a token, when it returns an error other than `authorization_pending` or `slow_down`, or when the lifetime of the device code has passed.

## Providers

An `OAuth2Provider` describes how to talk to one service. Three providers are included, each a case class in the `OAuth2Provider` companion object:

| Provider                                        | Parameters from the service's developer console | Scope separator | Sends refresh requests |
| ----------------------------------------------- | ----------------------------------------------- | --------------- | ---------------------- |
| `OAuth2Provider.Github(clientId)`               | The client ID of an OAuth app                   | `,`             | Yes                    |
| `OAuth2Provider.Google(clientId, clientSecret)` | The client ID and client secret                 | space           | Yes                    |
| `OAuth2Provider.Facebook(appId, clientToken)`   | The app ID and client token                     | `,`             | No                     |

The service must allow the device flow for the registered application. On GitHub, enable the device flow in the settings of the OAuth app. On Google, create an OAuth client of type "TVs and Limited Input devices". On Facebook, enable the device login option in the Facebook Login settings of the app.

We create one option per provider. Google requires at least one scope, so its example requests the `openid` and `email` scopes:

```scala
val githubLogin: Options[OAuth2Token] =
  Options.oauth2(OAuth2Provider.Github("github-client-id"), List("repo"))

val googleLogin: Options[OAuth2Token] =
  Options.oauth2(OAuth2Provider.Google("google-client-id", "google-client-secret"), List("openid", "email"))

val facebookLogin: Options[OAuth2Token] =
  Options.oauth2(OAuth2Provider.Facebook("facebook-app-id", "facebook-client-token"), List("public_profile"))
```

A provider for any other service that supports the device flow is a class that extends `OAuth2Provider`, as described in [Custom Providers](#custom-providers).

## Token Storage

The token is stored as JSON in a file in the user's home directory. The file name is `oauth2_<provider>_<client>_access_token.json`, where `<provider>` is the lower-case name of the provider, such as `github`, and `<client>` is its client identifier: the client ID for GitHub and Google, the app ID for Facebook. The `--oauth2-file <path>` option sets another file.

The file holds the access token, its type, its expiry time, the refresh token and the granted scopes, in plain text. Anyone who can read the file can use the token, so protect it like a password. Deleting the file makes the next run ask for authorization again.

## Using the Token

The command receives an `OAuth2Token`, which gives access to the current access token:

```scala
trait OAuth2Token {
  def accessToken: Task[AccessToken]
  def refreshTokenNow: Task[Unit]
}
```

`OAuth2Token#accessToken` returns the current token. If the token has expired, it is refreshed first, with the refresh token when there is one and otherwise by running the authorization again. `OAuth2Token#refreshTokenNow` refreshes the token immediately. Call it when the service rejects a token before its expiry time, for example because the user revoked it, which ZIO CLI cannot detect.

`AccessToken` is a case class with these fields:

```scala
final case class AccessToken(
  accessToken: String,
  tokenType: TokenType,
  expiresAt: Option[OffsetDateTime],
  refreshToken: Option[String],
  scope: List[String]
)
```

- `AccessToken#accessToken` is the token string to send to the API. Treat it as a secret and do not print it.
- `AccessToken#tokenType` says how the token must be sent.
- `AccessToken#expiresAt` is the expiry time, or `None` when the service gave no lifetime. A token without an expiry time is never refreshed automatically.
- `AccessToken#refreshToken` is the refresh token, when the service issued one.
- `AccessToken#scope` is the list of scopes the service reported as granted.

`TokenType` has three cases: `TokenType.Bearer`, `TokenType.Mac` and `TokenType.Other(tokenType)`, which keeps any other value as the service sent it.

:::caution
`TokenType.Bearer` and `TokenType.Mac` match only the lower-case strings `bearer` and `mac`. A service that sends `Bearer`, as Google does, produces `TokenType.Other("Bearer")`. Compare the type case-insensitively.
:::

We print the type of the GitHub token without printing the token itself:

```scala
import zio._

val tokenType = Command("token-type", Options.oauth2(OAuth2Provider.Github("github-client-id"), List("read:user")))

val tokenTypeApp = CliApp.make(
  name = "token-type",
  version = "1.0.0",
  summary = HelpDoc.Span.text("Shows the type of the GitHub token"),
  command = tokenType
) { (token: OAuth2Token) =>
  for {
    current <- token.accessToken
    isBearer = current.tokenType match {
                 case TokenType.Bearer       => true
                 case TokenType.Other(other) => other.equalsIgnoreCase("bearer")
                 case TokenType.Mac          => false
               }
    _ <- Console.printLine(s"Bearer token: $isBearer")
  } yield ()
}
```

## Custom Providers

To support another service, extend the `OAuth2Provider` trait. The service must support the device authorization flow, and the provider builds the HTTP requests of that flow with the JDK's `java.net.http.HttpRequest`:

```scala
trait OAuth2Provider {
  def name: String
  def clientIdentifier: String
  def authorizationRequest(scope: List[String]): HttpRequest
  def accessTokenRequest(authorization: AuthorizationResponse): HttpRequest
  def refreshTokenRequest(refreshToken: String): Option[HttpRequest]
  def decodeAuthorizationResponse(body: String): Either[String, AuthorizationResponse]
  def decodeAccessTokenResponse(body: String): Either[String, AccessTokenResponse]
}
```

| Member                                       | What it does                                                                                                    |
| -------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `OAuth2Provider#name`                        | The name shown in the help output. Its lower-case form is part of the default token file name.                  |
| `OAuth2Provider#clientIdentifier`            | The public client ID. It is part of the default token file name.                                                |
| `OAuth2Provider#authorizationRequest`        | Builds the request for a device code, with the requested scopes.                                                |
| `OAuth2Provider#accessTokenRequest`          | Builds the polling request for the token, from the device code in the `AuthorizationResponse`.                  |
| `OAuth2Provider#refreshTokenRequest`         | Builds the request that exchanges a refresh token for a new token, or returns `None` when the service has none. |
| `OAuth2Provider#decodeAuthorizationResponse` | Decodes the answer to the device-code request. The default reads the standard JSON of RFC 8628.                 |
| `OAuth2Provider#decodeAccessTokenResponse`   | Decodes each answer to the token request. The default reads the standard JSON of RFC 6749.                      |

The first five members are abstract. Override the two decoders only when the service does not answer with the standard JSON fields. The device-code request must answer with HTTP status 200, and so must a refresh request. Answers to the polling request are decoded whatever their status.

`AuthorizationResponse` is the decoded answer to the device-code request:

```scala
final case class AuthorizationResponse(
  deviceCode: String,
  userCode: String,
  verificationUri: String,
  verificationUriComplete: Option[String],
  expiresIn: Duration,
  interval: Duration
)
```

`deviceCode` is the value to send back in `OAuth2Provider#accessTokenRequest`. `userCode` and `verificationUri` are shown to the user. `expiresIn` is how long the codes are valid, and `interval` is the wait between polling requests, 5 seconds when the JSON has no `interval` field.

`AccessTokenResponse` is the decoded answer to a token request, and has two cases. `AccessTokenResponse.AccessToken(accessToken, tokenType, expiresIn, refreshToken, scope)` is a granted token, with its lifetime as a `Duration`. `AccessTokenResponse.Error(error, errorDescription, errorUri, interval)` is an error answer. Its `error` is an `AccessTokenResponse.Error.Kind`: `AuthorizationPending` and `SlowDown` keep the polling going, and every other kind ends it. The other kinds are `InvalidRequest`, `InvalidClient`, `InvalidGrant`, `UnauthorizedClient`, `UnsupportedGrantType`, `InvalidScope`, `AccessDenied`, `ExpiredToken` and `Other(code)`.

[How to log users in with OAuth2](how-to-oauth2-login.md) shows a complete custom provider.

## Platform Support

`Options.oauth2` is defined only in the JVM build of ZIO CLI. On Scala.js and Scala Native, code that calls it does not compile.

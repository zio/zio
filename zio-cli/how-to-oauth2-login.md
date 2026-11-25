# How to log users in with OAuth2

> We want a command-line tool that calls a web API on behalf of its user, for example to read the user's GitHub profile. The user logs in once in a browser, and the tool keeps the access token for later runs. ZIO CLI does this with `Options.oauth2` and the OAuth 2.0 device authorization flow, described in [Authorization with OAuth2](auth.md).

You need:

- A JVM build of the application. `Options.oauth2` does not exist on Scala.js or Scala Native.
- An application registered with the service, with the device flow enabled. For GitHub, create an OAuth app, enable the device flow in the app's settings, and copy its client ID.
- The scopes your API calls need. Reading the user's profile on GitHub needs `read:user`.

## Steps

The first four steps build a `whoami` command that prints the GitHub profile of the user. The last step replaces the GitHub provider with one for another service.

### 1. Require a Token in the Command

Add an option created with `Options.oauth2`, giving the provider and the scopes. The command then produces an `OAuth2Token`:

```scala
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import zio._
import zio.cli._
import zio.cli.oauth2._

val githubClientId = "Iv1.0123456789abcdef"

val login: Options[OAuth2Token] =
  Options.oauth2(OAuth2Provider.Github(githubClientId), List("read:user"))

val whoami: Command[OAuth2Token] = Command("whoami", login)
```

Parsing this command obtains the token before your code runs. On the first run it prints a web address and a code and waits until the user approves the application in a browser.

### 2. Send the Token with Each API Request

Read the current token with `OAuth2Token#accessToken` and put it in the `Authorization` header. `OAuth2Token#accessToken` refreshes an expired token before returning it:

```scala
val http: HttpClient = HttpClient.newHttpClient()

def fetchProfile(token: OAuth2Token): Task[HttpResponse[String]] =
  for {
    current  <- token.accessToken
    request   = HttpRequest
                  .newBuilder(URI.create("https://api.github.com/user"))
                  .header("Authorization", s"Bearer ${current.accessToken}")
                  .header("Accept", "application/vnd.github+json")
                  .build()
    response <- ZIO.fromCompletableFuture(http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
  } yield response
```

Never print `AccessToken#accessToken` or write it to a log. It gives full access to the scopes the user granted.

### 3. Refresh a Rejected Token Once

A service can reject a token before its expiry time, for example after the user revoked it. ZIO CLI cannot detect that, so when the API answers `401 Unauthorized`, call `OAuth2Token#refreshTokenNow` and try once more:

```scala
def fetchProfileWithRetry(token: OAuth2Token): Task[HttpResponse[String]] =
  fetchProfile(token).flatMap { response =>
    if (response.statusCode() == 401) token.refreshTokenNow *> fetchProfile(token)
    else ZIO.succeed(response)
  }
```

`OAuth2Token#refreshTokenNow` uses the refresh token when the service issued one. GitHub OAuth apps issue refresh tokens only when expiring user tokens are enabled for the app. Without a refresh token, it runs the browser authorization again.

### 4. Run the Command in a CliApp

Build the application with `CliApp.make` and print the API's answer:

```scala
object WhoAmI extends ZIOCliDefault {
  val cliApp = CliApp.make(
    name = "whoami",
    version = "1.0.0",
    summary = HelpDoc.Span.text("Prints your GitHub profile"),
    command = whoami
  ) { token =>
    fetchProfileWithRetry(token).flatMap(response => Console.printLine(response.body()))
  }
}
```

The first run prints the address and code and waits. Later runs read the token from `~/oauth2_github_<client id>_access_token.json` and print the profile at once. To keep the token somewhere else, pass `--oauth2-file <path>`. To log the user out, delete the file.

When the user denies access or the code expires, the run ends with the service's error message, printed like an input error.

### 5. Write a Provider for Another Service

Any service that supports the device authorization flow of RFC 8628 can be used. Its documentation lists two addresses: the device authorization endpoint and the token endpoint. A provider extends `OAuth2Provider` and builds the three requests of the flow. The provider below sends form-encoded `POST` requests, which is what RFC 8628 specifies. Replace the `example.com` addresses with the endpoints of your service:

```scala
import java.net.URLEncoder

class DeviceFlowProvider(
  val name: String,
  clientId: String,
  deviceAuthorizationEndpoint: URI,
  tokenEndpoint: URI
) extends OAuth2Provider {
  val clientIdentifier: String = clientId

  private def post(uri: URI, fields: (String, String)*): HttpRequest = {
    val body = fields.map { case (key, value) => s"$key=${URLEncoder.encode(value, "UTF-8")}" }.mkString("&")
    HttpRequest
      .newBuilder(uri)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Accept", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
  }

  def authorizationRequest(scope: List[String]): HttpRequest =
    post(deviceAuthorizationEndpoint, "client_id" -> clientId, "scope" -> scope.mkString(" "))

  def accessTokenRequest(authorization: AuthorizationResponse): HttpRequest =
    post(
      tokenEndpoint,
      "client_id"   -> clientId,
      "device_code" -> authorization.deviceCode,
      "grant_type"  -> "urn:ietf:params:oauth:grant-type:device_code"
    )

  def refreshTokenRequest(refreshToken: String): Option[HttpRequest] =
    Some(post(tokenEndpoint, "client_id" -> clientId, "grant_type" -> "refresh_token", "refresh_token" -> refreshToken))
}

val example = new DeviceFlowProvider(
  name = "Example",
  clientId = "example-client-id",
  deviceAuthorizationEndpoint = URI.create("https://auth.example.com/oauth/device"),
  tokenEndpoint = URI.create("https://auth.example.com/oauth/token")
)

val exampleLogin: Options[OAuth2Token] = Options.oauth2(example, List("profile"))
```

Return `None` from `OAuth2Provider#refreshTokenRequest` when the service has no refresh tokens. The token is then renewed by running the authorization again. The provider's `name` appears in the help output and, in lower case, in the default token file name, so keep it short and stable.

The default decoders read the standard JSON answers. If the service names a field differently, override the matching decoder. This service answers the device-code request with `verification_url` instead of `verification_uri`, so we decode that answer ourselves with zio-json, which ZIO CLI already depends on:

```scala
import zio.json._

case class LegacyDeviceCode(
  device_code: String,
  user_code: String,
  verification_url: String,
  expires_in: Long,
  interval: Long
)

object LegacyDeviceCode {
  implicit val decoder: JsonDecoder[LegacyDeviceCode] = DeriveJsonDecoder.gen[LegacyDeviceCode]
}

class LegacyProvider(clientId: String, deviceEndpoint: URI, tokenEndpoint: URI)
    extends DeviceFlowProvider("Legacy", clientId, deviceEndpoint, tokenEndpoint) {
  override def decodeAuthorizationResponse(body: String): Either[String, AuthorizationResponse] =
    body.fromJson[LegacyDeviceCode].map { answer =>
      AuthorizationResponse(
        deviceCode = answer.device_code,
        userCode = answer.user_code,
        verificationUri = answer.verification_url,
        verificationUriComplete = None,
        expiresIn = Duration.fromSeconds(answer.expires_in),
        interval = Duration.fromSeconds(answer.interval)
      )
    }
}
```

Use the new provider with `Options.oauth2` exactly like a built-in one. [Authorization with OAuth2](auth.md) lists every member of `OAuth2Provider`, the fields of `AuthorizationResponse`, and the cases of `AccessTokenResponse` that the token decoder returns.

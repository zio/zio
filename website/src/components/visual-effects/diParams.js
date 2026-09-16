// website/src/components/visual-effects/diParams.js
//
// Single source of truth for the Dependency Injection tab's snippet, shared
// by the Code toggle (CodeShowcase/data.js) and the Visual view's embedded
// example (scenarios/DependencyInjectionVisual.jsx) so the two can't drift —
// the same arrangement streamingParams.js uses, for the same reason.
//
// Deliberately dependency-free: data.js is imported during Docusaurus's Node
// prerender and must not pull the visual chunk's `effect`/`motion` imports
// into the main bundle.
//
// Trimmed to the wiring itself (the UserService class body is implied) so the
// snippet leaves room for the layer graph inside the panel's fixed height.
export const DI_SNIPPET = `object UserService:
  val live: ZLayer[Database & Logger, Nothing, UserService] =
    ZLayer.fromFunction(new UserService(_, _))

val app: ZIO[UserService, Throwable, User] =
  ZIO.serviceWithZIO[UserService](_.signup("John"))

// forget a layer and this is a compile error, not a 3am page
val runnable = app.provide(UserService.live, Database.live, Logger.live)`;

// The layers the visual builds, and what each one waits on. Mirrors the
// `provide` call above: Database and Logger have no dependencies so they are
// constructed in parallel, and UserService waits for both.
export const DI_LAYERS = [
  { id: 'Database', label: 'Database.live', dependsOn: [] },
  { id: 'Logger', label: 'Logger.live', dependsOn: [] },
  {
    id: 'UserService',
    label: 'UserService.live',
    dependsOn: ['Database', 'Logger'],
  },
];

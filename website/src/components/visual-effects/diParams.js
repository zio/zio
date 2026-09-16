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
// Two services deliberately require Database: that is what makes the sharing
// visible — it is written once in `provide`, built once at runtime, and the
// same instance is handed to both. The UserService/AuditService class bodies are
// implied (they live in the compile-check stubs) to keep the snippet short
// enough to sit beside the graph in the panel's fixed height.
export const DI_SNIPPET = `object UserService:
  val live: ZLayer[Database & Logger, Nothing, UserService] =
    ZLayer.fromFunction(new UserService(_, _))

object AuditService:
  val live: ZLayer[Database, Nothing, AuditService] =
    ZLayer.fromFunction(new AuditService(_))

// Database.live is written once and built once — both services share it
val runnable =
  app.provide(UserService.live, AuditService.live, Database.live, Logger.live)`;

// The layers the visual builds and what each one requires. Mirrors the
// `provide` call above: Database and Logger require nothing so the runtime
// constructs them concurrently; UserService and AuditService each pull
// Database out of the environment and therefore wait for it.
export const DI_LAYERS = [
  { id: 'Database', label: 'Database.live', dependsOn: [] },
  { id: 'Logger', label: 'Logger.live', dependsOn: [] },
  {
    id: 'UserService',
    label: 'UserService.live',
    dependsOn: ['Database', 'Logger'],
  },
  { id: 'AuditService', label: 'AuditService.live', dependsOn: ['Database'] },
];

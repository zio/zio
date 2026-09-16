import { Context, Effect, Layer } from 'effect';
import { useEffect, useMemo } from 'react';
import { DI_LAYERS, DI_SNIPPET } from '../diParams';
import { EffectExample } from '../EffectExample';
import { getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { LayerGraph } from '../LayerGraph';
import { LayerGraphView } from '../layers/LayerGraphView';
import { StringResult } from '../renderers';

// Bespoke — the source visual-effect project has no DI/layer example to port,
// so this is built by hand rather than ported verbatim (the same approved
// exception as the Streaming tab). It still runs a REAL `effect` Layer graph:
// the construction order, and the fact that independent layers are built
// concurrently, come from the runtime, not from a scripted animation.
//
// What this deliberately does NOT show: the tab's headline claim is a
// compile-time one ("forget a layer and the build fails"), and no running
// effect can demonstrate that. Faking a compile error would be the one kind
// of animation this project doesn't do. The snippet carries that half.

// A range per layer rather than one shared range: with everything taking
// roughly the same time the two independent layers finished together, so
// "UserService waits for its dependencies" flashed past. Spreading them makes
// the fast one land well before the slow one, and the dependent visibly waits
// on whichever is slowest — which is the actual rule.
const BUILD_MS = {
  Database: [2600, 3400],
  Logger: [700, 1100],
  UserService: [1200, 1600],
  AuditService: [900, 1300],
  runnable: [800, 1200],
};

const buildDelay = (id) => getDelay(...BUILD_MS[id]);

const DatabaseTag = Context.GenericTag('Database');
const LoggerTag = Context.GenericTag('Logger');
const UserServiceTag = Context.GenericTag('UserService');
const AuditServiceTag = Context.GenericTag('AuditService');

// Hash-style identities rather than @1/@2/@3: sequential ordinals still read
// as generic labels, and two of them sitting side by side are easy to mistake
// for each other. The point of the graph is that one id turns up under two
// consumers, so the ids have to be unmistakably distinct. Uniqueness is
// enforced rather than assumed — a collision would quietly claim two separate
// instances were the same one, which is the exact thing being demonstrated.
const usedInstanceIds = new Set();

function nextInstance(name) {
  let id;
  do {
    // padStart because a small enough random value yields a short (or empty)
    // hex fragment, which would render as "Database@".
    id = Math.random().toString(16).slice(2, 6).padStart(4, '0');
  } while (usedInstanceIds.has(id));

  usedInstanceIds.add(id);
  return `${name}@${id}`;
}

// Layer.effect, not Layer.succeed, so construction is an effect we can time —
// and so the runtime genuinely decides when each one runs.
function buildLayer(graph, tag, id, make) {
  return Layer.effect(
    tag,
    Effect.gen(function* () {
      const durationMs = buildDelay(id);
      graph.startBuild(id, durationMs);
      yield* Effect.sleep(durationMs);
      const service = make();
      graph.setReady(id, service.instance);
      return service;
    }),
  );
}

function buildProgram(graph) {
  const databaseLive = buildLayer(graph, DatabaseTag, 'Database', () => ({
    instance: nextInstance('Database'),
    insert: (name) => Effect.succeed(name),
  }));

  const loggerLive = buildLayer(graph, LoggerTag, 'Logger', () => ({
    instance: nextInstance('Logger'),
    info: () => Effect.void,
  }));

  const userServiceLive = Layer.effect(
    UserServiceTag,
    Effect.gen(function* () {
      // Pulling its dependencies out of the environment is what makes the
      // runtime wait for them — nothing here sequences the build by hand.
      const db = yield* DatabaseTag;
      const logger = yield* LoggerTag;
      // The moment a second copy would have been constructed if layers were
      // not shared — record which instance actually came back. UserService
      // requires Database & Logger, so both pulls are recorded.
      graph.recordUse('Database', 'UserService', db.instance);
      graph.recordUse('Logger', 'UserService', logger.instance);

      const durationMs = buildDelay('UserService');
      graph.startBuild('UserService', durationMs);
      yield* Effect.sleep(durationMs);
      graph.setReady('UserService', nextInstance('UserService'));

      return {
        signup: (name) =>
          Effect.gen(function* () {
            yield* logger.info(name);
            return yield* db.insert(name);
          }),
      };
    }),
  );

  const auditServiceLive = Layer.effect(
    AuditServiceTag,
    Effect.gen(function* () {
      const db = yield* DatabaseTag;
      graph.recordUse('Database', 'AuditService', db.instance);

      const durationMs = buildDelay('AuditService');
      graph.startBuild('AuditService', durationMs);
      yield* Effect.sleep(durationMs);
      graph.setReady('AuditService', nextInstance('AuditService'));

      return { record: () => Effect.void };
    }),
  );

  // `app` itself is a node in the graph rather than a separate result box:
  // it can only run once every requirement in its R has been provided, which
  // is the last step of the story the graph is telling.
  const app = Effect.gen(function* () {
    const userService = yield* UserServiceTag;
    yield* AuditServiceTag;

    const durationMs = buildDelay('runnable');
    graph.startBuild('runnable', durationMs);
    yield* Effect.sleep(durationMs);

    const name = yield* userService.signup('John');
    graph.setReady('runnable', `signed up ${name}`);
    return new StringResult(`signed up ${name}`);
  });

  // Database and Logger have nothing to wait on, so the runtime constructs
  // them concurrently; UserService is provided them and therefore starts only
  // once both are ready.
  // Database.live is supplied once here, to both services at once — which is
  // exactly why the runtime constructs it once and shares the result.
  return app.pipe(
    Effect.provide(
      Layer.mergeAll(userServiceLive, auditServiceLive).pipe(
        Layer.provide(Layer.merge(databaseLive, loggerLive)),
      ),
    ),
  );
}

export default function DependencyInjectionVisual() {
  const graph = useMemo(() => new LayerGraph(DI_LAYERS), []);

  const appTask = useVisualEffect('app', () => buildProgram(graph), {
    deps: [graph],
  });

  // Same pattern as the other scenarios: reset the visual side-channel when
  // the task returns to idle, and on unmount alongside the fiber reset that
  // EffectExample already does.
  useEffect(() => {
    const unsubscribe = appTask.subscribe(() => {
      if (appTask.state.type === 'idle') {
        usedInstanceIds.clear();
        graph.reset();
      }
    });

    return () => {
      unsubscribe();
      usedInstanceIds.clear();
      graph.reset();
    };
  }, [appTask, graph]);

  return (
    <EffectExample
      name="ZLayer"
      description="Services are wired from their dependencies, in dependency order"
      code={DI_SNIPPET}
      effects={[appTask]}
      effectHighlightMap={{ app: { text: 'runnable' } }}
      layerGraph={graph}
      // The graph already shows this effect's progress and its result on the
      // app.provide(...) node, so the standard node row would just repeat the
      // header's run state.
      showEffectNodes={false}
      exampleId="zlayer-wiring"
    />
  );
}

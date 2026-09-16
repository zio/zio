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

const BUILD_MIN_MS = 1200;
const BUILD_MAX_MS = 1800;

const DatabaseTag = Context.GenericTag('Database');
const LoggerTag = Context.GenericTag('Logger');
const UserServiceTag = Context.GenericTag('UserService');

// Layer.effect, not Layer.succeed, so construction is an effect we can time —
// and so the runtime genuinely decides when each one runs.
function buildLayer(graph, tag, id, make) {
  return Layer.effect(
    tag,
    Effect.gen(function* () {
      const durationMs = getDelay(BUILD_MIN_MS, BUILD_MAX_MS);
      graph.startBuild(id, durationMs);
      yield* Effect.sleep(durationMs);
      graph.setReady(id);
      return make();
    }),
  );
}

function buildProgram(graph) {
  const databaseLive = buildLayer(graph, DatabaseTag, 'Database', () => ({
    insert: (name) => Effect.succeed(name),
  }));

  const loggerLive = buildLayer(graph, LoggerTag, 'Logger', () => ({
    info: () => Effect.void,
  }));

  const userServiceLive = Layer.effect(
    UserServiceTag,
    Effect.gen(function* () {
      // Pulling its dependencies out of the environment is what makes the
      // runtime wait for them — nothing here sequences the build by hand.
      const db = yield* DatabaseTag;
      const logger = yield* LoggerTag;

      const durationMs = getDelay(BUILD_MIN_MS, BUILD_MAX_MS);
      graph.startBuild('UserService', durationMs);
      yield* Effect.sleep(durationMs);
      graph.setReady('UserService');

      return {
        signup: (name) =>
          Effect.gen(function* () {
            yield* logger.info(name);
            return yield* db.insert(name);
          }),
      };
    }),
  );

  const app = Effect.gen(function* () {
    const userService = yield* UserServiceTag;
    const name = yield* userService.signup('John');
    return new StringResult(`signed up ${name}`);
  });

  // Database and Logger have nothing to wait on, so the runtime constructs
  // them concurrently; UserService is provided them and therefore starts only
  // once both are ready.
  return app.pipe(
    Effect.provide(
      userServiceLive.pipe(
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
        graph.reset();
      }
    });

    return () => {
      unsubscribe();
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
      exampleId="zlayer-wiring"
    />
  );
}

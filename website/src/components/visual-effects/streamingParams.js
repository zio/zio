// website/src/components/visual-effects/streamingParams.js
//
// Single source of truth for the Streaming tab's pipeline parameters and
// the snippet that displays them. The Code toggle (CodeShowcase/data.js)
// and the Visual view's embedded example (scenarios/StreamingVisual.jsx)
// must show the same example — including the same numbers — and they drifted
// once already (the snippet advertised mapZIOPar(20)/buffer(16) while the
// running pipeline used 4/2). Building the snippet from the constants the
// pipeline is actually configured with makes that drift impossible.
//
// Deliberately dependency-free: data.js is imported during Docusaurus's
// Node prerender, so this must not pull in `effect`, `motion`, or anything
// else that belongs in the lazy-loaded visual chunk.
export const STREAM_CONCURRENCY = 4;
export const STREAM_BUFFER_CAPACITY = 2;

const line = (code, comment) => `    ${code.padEnd(21)} // ${comment}`;

export const STREAMING_SNIPPET = [
  'val pipeline: ZIO[Any, Throwable, Unit] =',
  '  ZStream',
  line('.fromIterable(events)', 'or Kafka, files, sockets…'),
  line(
    `.mapZIOPar(${STREAM_CONCURRENCY})(enrich)`,
    `${STREAM_CONCURRENCY} concurrent enrichments`,
  ),
  line(`.buffer(${STREAM_BUFFER_CAPACITY})`, 'bounded queue'),
  '    .runDrain',
].join('\n');

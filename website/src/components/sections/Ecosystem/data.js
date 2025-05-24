export const ecosystemProjects = [
  {
    name: 'ZIO HTTP',
    description: 'Type-safe, purely functional HTTP library for building high-performance web applications and APIs',
    features: [
      'High-performance server based on Netty',
      'Type-safe and type-driven endpoints',
      'Support for both imperative and declarative endpoints',
      'Designed for cloud-native environments',
      'Support for both server and client applications',
      'WebSocket support for real-time applications',
      'Middleware system for cross-cutting concerns',
      'Integration with ZIO Schema for automatic codecs',
      'Built-in support for streaming responses',
    ],
    link: 'https://ziohttp.com',
    icon: '🌐'
  },
  {
    name: 'ZIO Streams',
    description: 'Powerful, composable, and type-safe streaming library for working with large or infinite data',
    features: [
      'Process infinite streams with finite memory resource',
      'Automatic backpressure handling',
      'Rich set of stream combinators',
      'Non-blocking and asynchronous processing',
    ],
    link: '/reference/stream',
    icon: '🔗'
  },
  {
    name: 'ZIO Test',
    description: 'Feature-rich testing framework with powerful assertions and property-based testing',
    features: [
      'Property-based testing out of the box',
      'Deterministic testing of concurrent code',
      'Test aspects for reusable configurations',
      'Integration with JUnit and other frameworks'
    ],
    link: '/reference/test',
    icon: '✅'
  },
  {
    name: 'ZIO STM',
    description: 'Software Transactional Memory for safe, composable concurrent programming',
    features: [
      'Atomic, isolated transactions',
      'Composable concurrent operations',
      'No deadlocks or race conditions',
      'Automatic retry of interrupted transactions'
    ],
    link: '/reference/stm',
    icon: '🔒'
  },
  {
    name: 'ZIO Schema',
    description: 'Declarative schema definitions for data structures with automatic derivation',
    features: [
      'Reification of data structures',
      'Manual and automatic schema derivation',
      'Built-in codecs for JSON, Protobuf, Avro, and Thrift',
      'Schema transformations and migrations',
    ],
    link: '/reference/schema',
    icon: '🧬'
  },
  {
    name: 'ZIO Config',
    description: 'Type-safe, composable configuration management with automatic documentation',
    features: [
      'Type-safe configuration descriptions',
      'Multiple source support (files, env vars)',
      'Automatic documentation generation',
      'Validation with detailed error reporting'
    ],
    link: '/ecosystem/officials/zio-config',
    icon: '⚙️'
  },
  {
    name: 'ZIO Logging',
    description: 'High-performance, structured logging with contextual information',
    features: [
      'Structured logging for ZIO applications',
      'Multiple backend support (SLF4J, JPL, Console)',
      'Context-aware logging with MDC support',
      'Log correlation across async boundaries'
    ],
    link: '/ecosystem/officials/zio-logging',
    icon: '📝'
  }
];

module.exports = {
  overview_sidebar:
    [
      "overview/overview_index",
      "overview/overview_creating_effects",
      "overview/overview_basic_operations",
      "overview/overview_handling_errors",
      "overview/overview_handling_resources",
      "overview/overview_basic_concurrency",
      "overview/overview_running_effects",
      "overview/overview_performance",
      "overview/overview_platforms"
    ],
  "reference-sidebar": [
    "reference/index",
    {
      type: "category",
      label: "Core",
      collapsed: false,
      items: [
        {
          type: "category",
          link: { type: 'doc', id: 'reference/core/zio/zio' },
          label: "ZIO",
          items: [
            {
              type: "category",
              collapsed: true,
              label: "Type Aliases",
              items: [
                "reference/core/zio/uio",
                "reference/core/zio/urio",
                "reference/core/zio/task",
                "reference/core/zio/rio",
                "reference/core/zio/io",
              ]
            }
          ]
        },
        "reference/core/zioapp",
        "reference/core/runtime",
        "reference/core/exit",
        "reference/core/cause"
      ]
    },
    {
      type: "category",
      label: "Error Management",
      link: { type: "doc", id: "reference/error-management/index" },
      items:
        [

          {
            type: "category",
            label: "Three Types of Errors in ZIO",
            link: { type: "doc", id: "reference/error-management/types/index" },
            items:
              [
                "reference/error-management/types/failures",
                "reference/error-management/types/defects",
                "reference/error-management/types/fatals"
              ]
          },

          "reference/error-management/imperative-vs-declarative",
          "reference/error-management/expected-and-unexpected-errors",
          "reference/error-management/exceptional-and-unexceptional-effects",
          "reference/error-management/typed-errors-guarantees",
          "reference/error-management/sequential-and-parallel-errors",

          {
            type: "category",
            label: "Recovering From Errors",
            items:
              [
                "reference/error-management/recovering/catching",
                "reference/error-management/recovering/fallback",
                "reference/error-management/recovering/folding",
                "reference/error-management/recovering/retrying",
                "reference/error-management/recovering/timing-out",
                "reference/error-management/recovering/sandboxing",
              ]
          },
          "reference/error-management/error-accumulation",
          {
            type: "category",
            label: "Error Channel Operations",
            items:
              [
                "reference/error-management/operations/map-operations",
                "reference/error-management/operations/chaining-effects-based-on-errors",
                "reference/error-management/operations/filtering-the-success-channel",
                "reference/error-management/operations/tapping-errors",
                "reference/error-management/operations/exposing-errors-in-the-success-channel",
                "reference/error-management/operations/exposing-the-cause-in-the-success-channel",
                "reference/error-management/operations/converting-defects-to-failures",
                "reference/error-management/operations/error-refinement",
                "reference/error-management/operations/flattening-optional-error-types",
                "reference/error-management/operations/merging-the-error-channel-into-the-success-channel",
                "reference/error-management/operations/flipping-error-and-success-channels",
                "reference/error-management/operations/rejecting-some-success-values",
                "reference/error-management/operations/zooming-in-on-nested-values",
              ]
          },
          {
            type: "category",
            label: "Best Practices",
            items:
              [
                "reference/error-management/best-practices/algebraic-data-types",
                "reference/error-management/best-practices/union-types",
                "reference/error-management/best-practices/unexpected-errors",
                "reference/error-management/best-practices/logging-errors"
              ]
          },
          "reference/error-management/debugging",
          "reference/error-management/logging",
          "reference/error-management/examples"

        ]
    },
    {
      type: "category",
      label: "Scheduling",
      link: { type: "doc", id: "reference/schedule/index" },
      items: [
        "reference/schedule/repetition",
        "reference/schedule/retrying",
        "reference/schedule/built-in-schedules",
        "reference/schedule/combinators",
        "reference/schedule/examples",
      ]
    },
    {
      type: "category",
      label: "Contextual Types",
      link: { type: "doc", id: "reference/contextual/index" },
      items:
        [
          "reference/contextual/zenvironment",
          {
            type: "category",
            label: "ZIO Layers",
            items: [
              "reference/contextual/zlayer",
              {
                type: "category",
                collapsed: true,
                label: "Type Aliases",
                items: [
                  "reference/contextual/rlayer",
                  "reference/contextual/ulayer",
                  "reference/contextual/layer",
                  "reference/contextual/urlayer",
                  "reference/contextual/tasklayer"
                ]
              }
            ]
          },
          {
            type: "category",
            label: "Built-in Services",
            link: { type: "doc", id: "reference/contextual/services/index" },
            items: [
              "reference/contextual/services/console",
              "reference/contextual/services/clock",
              "reference/contextual/services/random",
              "reference/contextual/services/system"
            ]
          }
        ]
    },
    {
      type: "category",
      label: "State Management",
      link: { type: "doc", id: "reference/state/index" },
      items:
        [
          "reference/state/sequential",
          {
            type: "category",
            label: "Concurrent",
            link: { type: "doc", id: "reference/state/concurrent" },
            items:
              [
                "reference/state/global-shared-state",
                {
                  type: "category",
                  label: "Fiber-local State",
                  link: { type: "doc", id: "reference/state/fiber-local-state" },
                  items:
                    [
                      "reference/state/fiberref",
                      "reference/state/zstate",
                    ]
                }
              ]
          },
        ]
    },
    {
      type: "category",
      label: "Concurrency",
      items: [
        {
          type: "category",
          label: "ZIO Fibers",
          link: { type: "doc", id: "reference/fiber/index" },
          items: [
            "reference/fiber/fiber",
            "reference/fiber/fiberid",
            "reference/fiber/fiberstatus"
          ]
        },
        {
          type: "category",
          label: "Synchronization",
          link: { type: "doc", id: "reference/sync/index" },
          items: [
            "reference/sync/reentrantlock",
            "reference/sync/countdownlatch",
            "reference/sync/cyclicbarrier",
            "reference/sync/concurrentmap",
            "reference/sync/concurrentset",
          ]
        },
        {
          type: "category",
          label: "Concurrency Primitives",
          link: { type: "doc", id: "reference/concurrency/index" },
          items: [
            {
              type: "category",
              label: "Mutable Reference",
              items: [
                "reference/concurrency/ref",
                "reference/concurrency/refsynchronized",
              ]
            },
            "reference/concurrency/promise",
            "reference/concurrency/queue",
            "reference/concurrency/hub",
            "reference/concurrency/semaphore"
          ]
        },
        {
          type: "category",
          label: "STM",
          link: { type: "doc", id: "reference/stm/index" },
          items: [
            "reference/stm/stm",
            "reference/stm/tarray",
            "reference/stm/trandom",
            "reference/stm/tset",
            "reference/stm/tmap",
            "reference/stm/tref",
            "reference/stm/tpriorityqueue",
            "reference/stm/tpromise",
            "reference/stm/tqueue",
            "reference/stm/treentrantlock",
            "reference/stm/tsemaphore",
            "reference/stm/thub",
          ]
        },
      ]
    },
    {
      type: "category",
      label: "Resource Management",
      link: { type: "doc", id: "reference/resource/index" },
      items: [
        "reference/resource/scope",
        "reference/resource/zpool",
      ]
    },
    {
      type: "category",
      label: "Streaming",
      link: { type: "doc", id: "reference/stream/index" },
      items: [
        {
          type: "category",
          label: "Main Components",
          items: [
            {
              type: "category",
              label: "ZStream",
              items: [
                "reference/stream/zstream",
                "reference/stream/stream",
                "reference/stream/ustream",
              ]
            },
            {
              type: "category",
              label: "ZPipeline",
              items: [
                "reference/stream/zpipeline",
              ]
            },
            {
              type: "category",
              label: "ZSink",
              items: [
                "reference/stream/zsink",
                "reference/stream/sink",
              ]
            },
            "reference/stream/zchannel"
          ]
        },
        "reference/stream/subscription-ref"
      ]
    },
    {
      type: "category",
      label: "Metrics",
      link: { type: "doc", id: "reference/metrics/index" },
      items: [
        {
          type: "category",
          label: "Metric Types",
          items: [
            "reference/metrics/counter",
            "reference/metrics/gauge",
            "reference/metrics/histogram",
            "reference/metrics/summary",
            "reference/metrics/setcount"
          ]
        },
        "reference/metrics/metriclabel",
        "reference/metrics/jvm",
      ]
    },
    {
      type: "category",
      label: "Testing",
      link: { type: "doc", id: "reference/test/index" },
      items: [
        "reference/test/spec",
        "reference/test/assertion",
        {
          type: "category",
          label: "Test Services",
          link: { type: "doc", id: "reference/test/environment/index" },
          items: [
            "reference/test/environment/console",
            "reference/test/environment/clock",
            "reference/test/environment/random",
            "reference/test/environment/system",
            "reference/test/environment/live",
            "reference/test/environment/config",
            "reference/test/environment/sized",
          ]
        },
        "reference/test/test-aspect",
        "reference/test/gen",
      ]
    },
    {
      type: "category",
      label: "Miscellaneous",
      items: [
        "reference/misc/chunk",
        "reference/misc/supervisor",
      ]
    }
  ],
  "guides-sidebar": [
    "guides/index",
    {
      type: "category",
      label: "Quickstart Guides",
      collapsed: false,
      items: [
        "guides/quickstarts/hello-world",
        "guides/quickstarts/restful-webservice",
        "guides/quickstarts/graphql-webservice",
      ]
    },
    {
      type: "category",
      label: "Tutorial Guides",
      items: [
        "guides/tutorials/configurable-zio-application",
        "guides/tutorials/encode-and-decode-json-data",
        "guides/tutorials/enable-logging-in-a-zio-application",
        "guides/tutorials/create-custom-logger-for-a-zio-application",
        "guides/tutorials/run-our-first-zio-project-with-vscode",
        "guides/tutorials/run-our-first-zio-project-with-intellij-idea",
        "guides/tutorials/deploy-a-zio-application-using-docker",
        "guides/tutorials/producing-consuming-data-from-kafka-topics",
        "guides/tutorials/monitor-a-zio-application-using-zio-built-in-metric-system",
        "guides/tutorials/debug-a-zio-application",
        "guides/tutorials/build-a-restful-webservice",
        "guides/tutorials/build-a-graphql-webservice",
      ]
    },
    {
      type: "category",
      label: "Integration Guides",
      items: [
        "guides/interop/with-cats-effect",
        "guides/interop/with-future",
        "guides/interop/with-java",
        "guides/interop/with-javascript",
        "guides/interop/with-monix",
        "guides/interop/with-scalaz-7x",
        "guides/interop/with-reactive-streams",
        "guides/interop/with-twitter",
        "guides/interop/with-guava"
      ]
    },
    {
      type: "category",
      label: "Migration Guides",
      collapsed: false,
      items: [
        "guides/migrate/zio-2.x-migration-guide",
        "guides/migrate/from-cats-effect",
        "guides/migrate/from-monix",
      ]
    }
  ],
  "resources-sidebar": [
    "resources/index",
    {
      type: "category",
      label: "Learning",
      collapsed: false,
      items: [
        "resources/learning/articles",
        "resources/learning/videos",
        "resources/learning/cookbooks",
        "resources/learning/cheatsheets",
        "resources/learning/sampleprojects",
        "resources/learning/poweredbyzio"
      ]
    },
    {
      type: "category",
      label: "Ecosystem",
      collapsed: false,
      items: [
        {
          type: "category",
          label: "Official Libraries",
          items: [
            "resources/ecosystem/officials/index",
            "resources/ecosystem/officials/zio-actors",
            "resources/ecosystem/officials/zio-akka-cluster",
            "resources/ecosystem/officials/zio-aws",
            "resources/ecosystem/officials/zio-cache",
            "resources/ecosystem/officials/zio-config",
            "resources/ecosystem/officials/zio-ftp",
            "resources/ecosystem/officials/zio-json",
            "resources/ecosystem/officials/zio-kafka",
            "resources/ecosystem/officials/zio-logging",
            "resources/ecosystem/officials/zio-metrics",
            "resources/ecosystem/officials/zio-mock",
            "resources/ecosystem/officials/zio-nio",
            "resources/ecosystem/officials/zio-optics",
            "resources/ecosystem/officials/zio-prelude",
            "resources/ecosystem/officials/zio-process",
            "resources/ecosystem/officials/zio-query",
            "resources/ecosystem/officials/zio-redis",
            "resources/ecosystem/officials/zio-rocksdb",
            "resources/ecosystem/officials/zio-s3",
            "resources/ecosystem/officials/zio-schema",
            "resources/ecosystem/officials/zio-sqs",
            "resources/ecosystem/officials/zio-telemetry",
            "resources/ecosystem/officials/zio-zmx",
          ]
        },
        {
          type: "category",
          label: "Community Libraries",
          items: [
            "resources/ecosystem/community/index",
            "resources/ecosystem/community/caliban",
            "resources/ecosystem/community/distage",
            "resources/ecosystem/community/logstage",
            "resources/ecosystem/community/munit-zio",
            "resources/ecosystem/community/quill",
            "resources/ecosystem/community/rezilience",
            "resources/ecosystem/community/tamer",
            "resources/ecosystem/community/tranzactio",
            "resources/ecosystem/community/zio-amqp",
            "resources/ecosystem/community/zio-arrow",
            "resources/ecosystem/community/zio-aws-s3",
            "resources/ecosystem/community/zio-grpc",
            "resources/ecosystem/community/zio-http",
            "resources/ecosystem/community/zio-k8s",
            "resources/ecosystem/community/zio-kinesis",
            "resources/ecosystem/community/zio-pulsar",
            "resources/ecosystem/community/zio-saga",
            "resources/ecosystem/community/zio-slick-interop",
            "resources/ecosystem/community/zio-test-akka-http",
          ],
        },
        "resources/ecosystem/compatible",
        "resources/ecosystem/tools",
        "resources/ecosystem/templates"
      ]
    }
  ],
  "about-sidebar":
    [
      "about/about_index",
      "about/faq",
      "about/coding-guidelines",
      "about/contributing",
      "about/contributing-to-documentation",
      "about/code-of-conduct",
      "about/users",
    ]
}

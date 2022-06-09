module.exports = {
  overview_sidebar: {
    Overview: [
      "overview/overview_index",
      "overview/overview_creating_effects",
      "overview/overview_basic_operations",
      "overview/overview_handling_errors",
      "overview/overview_handling_resources",
      "overview/overview_basic_concurrency",
      "overview/overview_testing_effects",
      "overview/overview_running_effects",
      "overview/overview_background",
      "overview/overview_performance",
      "overview/overview_platforms"
    ]
  },
  "datatypes-sidebar": {
    "Overview": [
      "datatypes/index"
    ],
    "Core Data Types": [
        {
            type: "category",
            label: "ZIO Effects",
            items: [
                "datatypes/core/zio/zio",
                "datatypes/core/zio/uio",
                "datatypes/core/zio/urio",
                "datatypes/core/zio/task",
                "datatypes/core/zio/rio",
                "datatypes/core/zio/io",
            ]
        },
        "datatypes/core/zioapp",
        "datatypes/core/runtime",
        "datatypes/core/exit",
        "datatypes/core/cause"
    ],
    "Contextual Types": [
        "datatypes/contextual/index",
        "datatypes/contextual/zenvironment",
        {
            type: "category",
            label: "ZIO Layers",
            items: [
                "datatypes/contextual/zlayer",
                "datatypes/contextual/rlayer",
                "datatypes/contextual/ulayer",
                "datatypes/contextual/layer",
                "datatypes/contextual/urlayer",
                "datatypes/contextual/tasklayer"
            ]
        },
        {
            type: "category",
            label: "Built-in Services",
            items: [
                "datatypes/contextual/services/index",
                "datatypes/contextual/services/console",
                "datatypes/contextual/services/clock",
                "datatypes/contextual/services/random",
                "datatypes/contextual/services/system"
            ]
        }
    ],
    "Concurrency": [
        {
            type: "category",
            label: "ZIO Fibers",
            items: [
                "datatypes/fiber/index",
                "datatypes/fiber/fiber",
                "datatypes/fiber/fiberref",
                "datatypes/fiber/fiberid",
                "datatypes/fiber/fiberstatus"
            ]
        },
        {
            type: "category",
            label: "Synchronization",
            items: [
                "datatypes/sync/index",
                "datatypes/sync/reentrantlock",
                "datatypes/sync/countdownlatch",
                "datatypes/sync/cyclicbarrier",
                "datatypes/sync/concurrentmap",
                "datatypes/sync/concurrentset",
            ]
        },
        {
            type: "category",
            label: "Concurrency Primitives",
            items: [
                "datatypes/concurrency/index",
                {
                    type: "category",
                    label: "Mutable References",
                    items: [
                        "datatypes/concurrency/ref",
                        "datatypes/concurrency/refsynchronized",
                    ]
                },
                "datatypes/concurrency/promise",
                "datatypes/concurrency/queue",
                "datatypes/concurrency/hub",
                "datatypes/concurrency/semaphore"
            ]
        },
        {
            type: "category",
            label: "STM",
            items: [
                "datatypes/stm/index",
                "datatypes/stm/stm",
                "datatypes/stm/tarray",
                "datatypes/stm/trandom",
                "datatypes/stm/tset",
                "datatypes/stm/tmap",
                "datatypes/stm/tref",
                "datatypes/stm/tpriorityqueue",
                "datatypes/stm/tpromise",
                "datatypes/stm/tqueue",
                "datatypes/stm/treentrantlock",
                "datatypes/stm/tsemaphore",
                "datatypes/stm/thub",
            ]
        },
    ],
    "Resource Management": [
      "datatypes/resource/index",
      "datatypes/resource/scope",
      "datatypes/resource/zpool",
    ],
    "Streaming": [
        "datatypes/stream/index",
        {
            type: "category",
            label: "Main Components",
            items: [
                {
                    type: "category",
                    label: "ZStream",
                    items: [
                        "datatypes/stream/zstream",
                        "datatypes/stream/stream",
                        "datatypes/stream/ustream",
                    ]
                },
                {
                    type: "category",
                    label: "ZPipeline",
                    items: [
                        "datatypes/stream/zpipeline",
                    ]
                },
                {
                    type: "category",
                    label: "ZSink",
                    items: [
                        "datatypes/stream/zsink",
                        "datatypes/stream/sink",
                    ]
                }
            ]
        },
        "datatypes/stream/subscription-ref"
    ],
      "Metrics": [
          "datatypes/metrics/index",
          {
              type: "category",
              label: "Metric Types",
              items: [
                  "datatypes/metrics/counter",
                  "datatypes/metrics/gauge",
                  "datatypes/metrics/histogram",
                  "datatypes/metrics/summary",
                  "datatypes/metrics/setcount"
              ]
          },
          "datatypes/metrics/metriclabel",
          "datatypes/metrics/jvm",
      ],
      "ZIO Test": [
        "datatypes/test/index",
        "datatypes/test/spec",
        "datatypes/test/assertion",
        {
          type: "category",
          label: "Test Services",
          items: [
            "datatypes/test/environment/index",
            "datatypes/test/environment/console",
            "datatypes/test/environment/clock",
            "datatypes/test/environment/random",
            "datatypes/test/environment/system",
            "datatypes/test/environment/live",
            "datatypes/test/environment/config",
            "datatypes/test/environment/sized",
          ]
        },
        "datatypes/test/test-aspect",
        "datatypes/test/gen",
      ],
    "Miscellaneous": [
      "datatypes/misc/chunk",
      "datatypes/misc/schedule",
      "datatypes/misc/supervisor",
      "datatypes/misc/zstate",
    ]
  },
  "guides-sidebar": [
    "guides/index",
    {
      "Quickstart Guides": [
        "guides/quickstarts/hello-world",
        "guides/quickstarts/restful-webservice", 
        "guides/quickstarts/graphql-webservice",
      ],
      "Tutorial Guides": [
        "guides/tutorials/configurable-zio-application",
        "guides/tutorials/encode-and-decode-json-data", 
        "guides/tutorials/enable-logging-in-a-zio-application",
        "guides/handle-errors"
      ],
      "Integration Guides": [
        "guides/interop/with-cats-effect",
        "guides/interop/with-future",
        "guides/interop/with-java",
        "guides/interop/with-javascript",
        "guides/interop/with-monix",
        "guides/interop/with-scalaz-7x",
        "guides/interop/with-reactive-streams",
        "guides/interop/with-twitter",
        "guides/interop/with-guava"
      ],
      "Migration Guides": [
        "guides/migrate/zio-2.x-migration-guide",
        "guides/migrate/from-cats-effect",
        "guides/migrate/from-monix",
      ]
    }
  ],
  "resources-sidebar": {
    "Overview": [
      "resources/index"
    ],
    "Learning": [
      "resources/learning/articles",
      "resources/learning/videos",
      "resources/learning/cookbooks",
      "resources/learning/cheatsheets",
      "resources/learning/sampleprojects",
      "resources/learning/poweredbyzio"
    ],
    "Ecosystem": [
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
            ],
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
  },
  "about-sidebar": {
    "About": [
      "about/index",
      "about/faq",
      "about/coding-guidelines",
      "about/contributing",
      "about/contributing-to-documentation",
      "about/code-of-conduct",
      "about/users",
    ]
  }
}

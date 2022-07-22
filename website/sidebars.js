module.exports = {
  overview_sidebar: {
    Overview: [
      "overview/overview_index",
      "overview/overview_creating_effects",
      "overview/overview_basic_operations",
      "overview/overview_handling_errors",
      "overview/overview_handling_resources",
      "overview/overview_basic_concurrency",
      "overview/overview_running_effects",
      "overview/overview_performance",
      "overview/overview_platforms"
    ]
  },
  "references-sidebar": {
    "References": [
      "references/index"
    ],
    "Core Data Types": [
        {
            type: "category",
            label: "ZIO Effects",
            items: [
                "references/core/zio/zio",
                "references/core/zio/error-management",
                "references/core/zio/uio",
                "references/core/zio/urio",
                "references/core/zio/task",
                "references/core/zio/rio",
                "references/core/zio/io",
            ]
        },
        "references/core/zioapp",
        "references/core/runtime",
        "references/core/exit",
        "references/core/cause"
    ],
    "Contextual Types": [
        "references/contextual/index",
        "references/contextual/zenvironment",
        {
            type: "category",
            label: "ZIO Layers",
            items: [
                "references/contextual/zlayer",
                "references/contextual/rlayer",
                "references/contextual/ulayer",
                "references/contextual/layer",
                "references/contextual/urlayer",
                "references/contextual/tasklayer"
            ]
        },
        {
            type: "category",
            label: "Built-in Services",
            items: [
                "references/contextual/services/index",
                "references/contextual/services/console",
                "references/contextual/services/clock",
                "references/contextual/services/random",
                "references/contextual/services/system"
            ]
        }
    ],
    "Concurrency": [
        {
            type: "category",
            label: "ZIO Fibers",
            items: [
                "references/fiber/index",
                "references/fiber/fiber",
                "references/fiber/fiberref",
                "references/fiber/fiberid",
                "references/fiber/fiberstatus"
            ]
        },
        {
            type: "category",
            label: "Synchronization",
            items: [
                "references/sync/index",
                "references/sync/reentrantlock",
                "references/sync/countdownlatch",
                "references/sync/cyclicbarrier",
                "references/sync/concurrentmap",
                "references/sync/concurrentset",
            ]
        },
        {
            type: "category",
            label: "Concurrency Primitives",
            items: [
                "references/concurrency/index",
                {
                    type: "category",
                    label: "Mutable References",
                    items: [
                        "references/concurrency/ref",
                        "references/concurrency/refsynchronized",
                    ]
                },
                "references/concurrency/promise",
                "references/concurrency/queue",
                "references/concurrency/hub",
                "references/concurrency/semaphore"
            ]
        },
        {
            type: "category",
            label: "STM",
            items: [
                "references/stm/index",
                "references/stm/stm",
                "references/stm/tarray",
                "references/stm/trandom",
                "references/stm/tset",
                "references/stm/tmap",
                "references/stm/tref",
                "references/stm/tpriorityqueue",
                "references/stm/tpromise",
                "references/stm/tqueue",
                "references/stm/treentrantlock",
                "references/stm/tsemaphore",
                "references/stm/thub",
            ]
        },
    ],
    "Resource Management": [
      "references/resource/index",
      "references/resource/scope",
      "references/resource/zpool",
    ],
    "Streaming": [
        "references/stream/index",
        {
            type: "category",
            label: "Main Components",
            items: [
                {
                    type: "category",
                    label: "ZStream",
                    items: [
                        "references/stream/zstream",
                        "references/stream/stream",
                        "references/stream/ustream",
                    ]
                },
                {
                    type: "category",
                    label: "ZPipeline",
                    items: [
                        "references/stream/zpipeline",
                    ]
                },
                {
                    type: "category",
                    label: "ZSink",
                    items: [
                        "references/stream/zsink",
                        "references/stream/sink",
                    ]
                },
                "references/stream/zchannel"
            ]
        },
        "references/stream/subscription-ref"
    ],
      "Metrics": [
          "references/metrics/index",
          {
              type: "category",
              label: "Metric Types",
              items: [
                  "references/metrics/counter",
                  "references/metrics/gauge",
                  "references/metrics/histogram",
                  "references/metrics/summary",
                  "references/metrics/setcount"
              ]
          },
          "references/metrics/metriclabel",
          "references/metrics/jvm",
      ],
      "Testing": [
        "references/test/index",
        "references/test/spec",
        "references/test/assertion",
        {
          type: "category",
          label: "Test Services",
          items: [
            "references/test/environment/index",
            "references/test/environment/console",
            "references/test/environment/clock",
            "references/test/environment/random",
            "references/test/environment/system",
            "references/test/environment/live",
            "references/test/environment/config",
            "references/test/environment/sized",
          ]
        },
        "references/test/test-aspect",
        "references/test/gen",
      ],
    "Miscellaneous": [
      "references/misc/chunk",
      "references/misc/schedule",
      "references/misc/supervisor",
      "references/misc/zstate",
    ]
  },
  "guides-sidebar": [
    {
      "Guides" : [
        "guides/index",
      ],
      "Quickstart Guides": [
        "guides/quickstarts/hello-world",
        "guides/quickstarts/restful-webservice", 
        "guides/quickstarts/graphql-webservice",
      ],
      "Tutorial Guides": [
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
    "Resources": [
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
      "about/about_index",
      "about/faq",
      "about/coding-guidelines",
      "about/contributing",
      "about/contributing-to-documentation",
      "about/code-of-conduct",
      "about/users",
    ]
  }
}

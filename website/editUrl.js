const ecosystemRepos = {
  'izumi-reflect': { repo: 'https://github.com/zio/izumi-reflect', branch: 'develop' },
  'zio2-interop-cats2': { repo: 'https://github.com/zio/interop-cats', branch: 'main' },
  'zio-amqp': { repo: 'https://github.com/zio/zio-amqp', branch: 'master' },
  'zio-aws': { repo: 'https://github.com/zio/zio-aws', branch: 'master' },
  'zio-blocks': { repo: 'https://github.com/zio/zio-blocks', branch: 'main' },
  'zio-bson': { repo: 'https://github.com/zio/zio-bson', branch: 'main' },
  'zio-cache': { repo: 'https://github.com/zio/zio-cache', branch: 'series/2.x' },
  'zio-cli': { repo: 'https://github.com/zio/zio-cli', branch: 'master' },
  'zio-config': { repo: 'https://github.com/zio/zio-config', branch: 'series/4.x' },
  'zio-direct': { repo: 'https://github.com/zio/zio-direct', branch: 'main' },
  'zio-dynamodb': { repo: 'https://github.com/zio/zio-dynamodb', branch: 'series/2.x' },
  'zio-ftp': { repo: 'https://github.com/zio/zio-ftp', branch: 'series/2.x' },
  'zio-json': { repo: 'https://github.com/zio/zio-json', branch: 'series/2.x' },
  'zio-kafka': { repo: 'https://github.com/zio/zio-kafka', branch: 'master' },
  'zio-lambda': { repo: 'https://github.com/zio/zio-lambda', branch: 'master' },
  'zio-logging': { repo: 'https://github.com/zio/zio-logging', branch: 'master' },
  'zio-metrics-connectors': { repo: 'https://github.com/zio/zio-metrics-connectors', branch: 'series/2.x' },
  'zio-parser': { repo: 'https://github.com/zio/zio-parser', branch: 'master' },
  'zio-prelude': { repo: 'https://github.com/zio/zio-prelude', branch: 'series/2.x' },
  'zio-process': { repo: 'https://github.com/zio/zio-process', branch: 'series/2.x' },
  'zio-profiling': { repo: 'https://github.com/zio/zio-profiling', branch: 'master' },
  'zio-query': { repo: 'https://github.com/zio/zio-query', branch: 'series/2.x' },
  'zio-quill': { repo: 'https://github.com/zio/zio-quill', branch: 'master' },
  'zio-redis': { repo: 'https://github.com/zio/zio-redis', branch: 'main' },
  'zio-rocksdb': { repo: 'https://github.com/zio/zio-rocksdb', branch: 'master' },
  'zio-s3': { repo: 'https://github.com/zio/zio-s3', branch: 'series/2.x' },
  'zio-sbt': { repo: 'https://github.com/zio/zio-sbt', branch: 'main' },
  'zio-schema': { repo: 'https://github.com/zio/zio-schema', branch: 'main' },
  'zio-sqs': { repo: 'https://github.com/zio/zio-sqs', branch: 'series/2.x' },
  'zio-streams-compress': { repo: 'https://github.com/zio/zio-streams-compress', branch: 'main' },
  'zio-telemetry': { repo: 'https://github.com/zio/zio-telemetry', branch: 'series/2.x' },

}

function getEditUrl({ docPath, versionDocsDirPath }) {
  const parts = docPath.split('/')
  const project = parts[0]
  const ecosystem = ecosystemRepos[project]

  if (versionDocsDirPath === 'docs' && ecosystem) {
    const relativePath = parts.slice(1).join('/')
    return `${ecosystem.repo}/edit/${ecosystem.branch}/docs/${relativePath}`
  }

  return `https://github.com/zio/zio/edit/series/2.x/${versionDocsDirPath}/${docPath}`
}

module.exports = {
  ecosystemRepos,
  getEditUrl,
}

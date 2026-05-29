import React from 'react';

export default function FAQSchema() {
  const faqSchema = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: [
      {
        '@type': 'Question',
        name: 'What is ZIO?',
        acceptedAnswer: {
          '@type': 'Answer',
          text: 'ZIO is a type-safe, composable library for asynchronous and concurrent programming in Scala. It provides a powerful set of abstractions for building scalable, resilient applications.',
        },
      },
      {
        '@type': 'Question',
        name: 'How do I get started with ZIO?',
        acceptedAnswer: {
          '@type': 'Answer',
          text: 'You can get started with ZIO by visiting the Getting Started guide in the documentation. It covers installation, basic concepts, and your first ZIO program.',
        },
      },
      {
        '@type': 'Question',
        name: 'Is ZIO production-ready?',
        acceptedAnswer: {
          '@type': 'Answer',
          text: 'Yes, ZIO is used in production by many companies. The library has been battle-tested and is actively maintained with regular releases.',
        },
      },
      {
        '@type': 'Question',
        name: 'How does ZIO compare to other effect libraries?',
        acceptedAnswer: {
          '@type': 'Answer',
          text: 'ZIO provides a unified, type-safe approach to effects with built-in support for concurrency, resource management, and error handling. See the documentation for detailed comparisons with other libraries.',
        },
      },
      {
        '@type': 'Question',
        name: 'Where can I get help with ZIO?',
        acceptedAnswer: {
          '@type': 'Answer',
          text: 'The ZIO community is active on Discord (https://discord.gg/2ccFBr4). You can also check the documentation, guides, and the GitHub repository for answers.',
        },
      },
    ],
  };

  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(faqSchema) }}
    />
  );
}

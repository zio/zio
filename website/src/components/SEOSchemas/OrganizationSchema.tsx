import React from 'react';
import Head from '@docusaurus/Head';

export default function OrganizationSchema() {
  const organizationSchema = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'ZIO',
    url: 'https://zio.dev',
    logo: 'https://zio.dev/img/zio.png',
    description:
      'Type-safe, composable asynchronous and concurrent programming for Scala',
    sameAs: [
      'https://github.com/zio/zio',
      'https://twitter.com/zioscala',
      'https://discord.gg/2ccFBr4',
    ],
    contactPoint: {
      '@type': 'ContactPoint',
      contactType: 'Community Support',
      url: 'https://discord.gg/2ccFBr4',
    },
  };

  const websiteSchema = {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    url: 'https://zio.dev',
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: 'https://zio.dev/search?q={search_term_string}',
      },
      'query-input': 'required name=search_term_string',
    },
  };

  return (
    <Head>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationSchema) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteSchema) }}
      />
    </Head>
  );
}

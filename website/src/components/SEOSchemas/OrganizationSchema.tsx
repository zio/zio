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

  return (
    <Head>
      <script
        key="organization-schema"
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationSchema) }}
      />
    </Head>
  );
}

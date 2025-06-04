import React from 'react';
import Layout from '@theme/Layout';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

import Hero from '@site/src/components/sections/Hero';
import Features from '@site/src/components/sections/Features';
import Ecosystem from '@site/src/components/sections/Ecosystem';
import Sponsors from '@site/src/components/sections/Sponsors';
import Zionomicon from '@site/src/components/sections/Zionomicon';

// Construct the home page from all components
export default function WelcomePage() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;
  return (
    <Layout
      title={`${siteConfig.title}`}
      description={`${siteConfig.tagline}`}
      image="/img/navbar_brand2x.png"
    >
      <Hero />

      <main>
        <Features />
        <Ecosystem title = "Ecosystem" subtitle="A rich ecosystem of libraries built on ZIO to solve real-world problems"/>
        <Zionomicon />
        <Sponsors />
      </main>
    </Layout>
  );
}

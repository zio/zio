import React, { useEffect } from 'react';
import Layout from '@theme/Layout';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

import Hero from '@site/src/components/sections/Hero';
import Features from '@site/src/components/sections/Features';
import CodeShowcase from '@site/src/components/sections/CodeShowcase';
import Ecosystem from '@site/src/components/sections/Ecosystem';
import Sponsors from '@site/src/components/sections/Sponsors';
import Zionomicon from '@site/src/components/sections/Zionomicon';
import Reveal from '@site/src/components/ui/Reveal';

// Construct the home page from all components
export default function WelcomePage() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;

  // Enable gentle section scroll-snapping on the homepage only (the class is
  // scoped in custom.css); remove it when leaving so docs pages scroll freely.
  useEffect(() => {
    const root = document.documentElement;
    root.classList.add('homepage-snap');
    return () => root.classList.remove('homepage-snap');
  }, []);

  return (
    <Layout
      title={`${siteConfig.title}`}
      description={`${siteConfig.tagline}`}
      image="/img/navbar_brand2x.png"
    >
      <Hero />

      <main>
        <Reveal>
          <CodeShowcase />
        </Reveal>
        <Reveal>
          <Features />
        </Reveal>
        <Reveal>
          <Ecosystem
            title="Ecosystem"
            subtitle="A rich ecosystem of libraries built on ZIO to solve real-world problems"
          />
        </Reveal>
        <Reveal>
          <Zionomicon />
        </Reveal>
        <Reveal>
          <Sponsors />
        </Reveal>
      </main>
    </Layout>
  );
}

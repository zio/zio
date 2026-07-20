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
import useFullpageScroll from '@site/src/components/ui/useFullpageScroll';

// Construct the home page from all components
export default function WelcomePage() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;

  // Homepage-only: mark <html> for scoped CSS (scroll offset) and enable the
  // fullPage-style one-section-per-scroll behavior over the marked sections.
  useEffect(() => {
    const root = document.documentElement;
    root.classList.add('homepage-snap');
    return () => root.classList.remove('homepage-snap');
  }, []);

  useFullpageScroll('.fp-section');

  return (
    <Layout
      title={`${siteConfig.title}`}
      description={`${siteConfig.tagline}`}
      image="/img/navbar_brand2x.png"
    >
      <div className="fp-section">
        <Hero />
      </div>

      <main>
        <Reveal className="fp-section">
          <CodeShowcase />
        </Reveal>
        <Reveal className="fp-section">
          <Features />
        </Reveal>
        <Reveal className="fp-section">
          <Ecosystem
            title="Ecosystem"
            subtitle="A rich ecosystem of libraries built on ZIO to solve real-world problems"
          />
        </Reveal>
        <Reveal className="fp-section">
          <Zionomicon />
        </Reveal>
        <Reveal className="fp-section">
          <Sponsors />
        </Reveal>
      </main>
    </Layout>
  );
}

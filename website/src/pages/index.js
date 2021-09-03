import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';

const baseUrl = '/';

// The features as pure objects 
const features = [
  {
    title: 'High-performance',
    content: 'Build scalable applications with 100x the performance of Scala’s Future',
  },
  {
    title: 'Type-safe',
    content: 'Use the full power of the Scala compiler to catch bugs at compile time',
  },
  {
    title: 'Concurrent',
    content: 'Easily build concurrent apps without deadlocks, race conditions, or complexity',
  },
  {
    title: 'Asynchronous',
    content: 'Write sequential code that looks the same whether it’s asynchronous or synchronous',
  },
  {
    title: 'Resource-safe',
    content: 'Build apps that never leak resources (including threads!), even when they fail',
  },
  {
    title: 'Testable',
    content: 'Inject test services into your app for fast, deterministic, and type-safe testing',
  },
  {
    title: 'Resilient',
    content: 'Build apps that never lose errors, and which respond to failure locally and flexibly',
  },
  {
    title: 'Functional',
    content: 'Rapidly compose solutions to complex problems from simple building blocks',
  },
];

const sponsors = [
  {
    title: '',
    image: `${baseUrl}img/ziverge.png`,
    imageAlt: 'Ziverge',
    imageLink: 'https://ziverge.com',
    imageAlign: 'top',
    content: 'Brilliant solutions for innovative companies'
  },
  {
    title: '',
    image: `${baseUrl}img/scalac.svg`,
    imageAlt: 'Scalac',
    imageLink: 'https://scalac.io/',
    imageAlign: 'top',
    content: 'Scale fast with Scala'
  },
  {
    title: '',
    image: `${baseUrl}img/septimal_mind.svg`,
    imageAlt: 'Septimal Mind',
    imageLink: 'https://7mind.io/',
    imageAlign: 'top',
    content: 'Inventing productivity'
  },
  {
    title: '',
    image: `https://files.softwaremill.com/logo/logo_vertical.png`,
    imageAlt: 'SoftwareMill',
    imageLink: 'https://softwaremill.com/',
    imageAlign: 'top',
    content: 'Custom software by true engineers'
  },
];

// How a single feature block is displayed 
function Feature(feature) {
  return (
    <div className={clsx('col col--3', styles.feature)}>
      <h3>{feature.title}</h3>
      <p>{feature.content}</p>
    </div>
  );
}

// How a single sponsor block is being displayed 
function Sponsor(sponsor) {
  return (
    <div className={clsx('col col--4', styles.feature)}>
      <p>{sponsor.content}</p>
    </div>
  );
}

// Construct the home page from all components 
function Home() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;
  return (
    <Layout
      title={`${siteConfig.title}`}
      description="Composable, functional integration flows">
      <header className={clsx('hero hero--primary', styles.heroBanner)}>
        <div className="container">
          <h1 className="hero__title">{siteConfig.title}</h1>
          <p className="hero__subtitle">{siteConfig.tagline}</p>
          <div className={styles.buttons}>
            <Link
              className={clsx(
                'button button--outline button--secondary button--lg',
                styles.getStarted,
              )}
              to={useBaseUrl('/getting_started')}>
              Get Started
            </Link>
          </div>
        </div>
      </header>
      <main>
        {features && features.length > 0 && (
          <section className={styles.features}>
            <div className="container">
              <div className="row">
                {features.map((props, idx) => (
                  <Feature key={idx} {...props} />
                ))}
              </div>
            </div>
          </section>
        )}
        {sponsors && sponsors.length > 0 && (
          <section className={styles.features}>
            <div className="container">
              <div className="row">
                {sponsors.map((props, idx) => (
                  <Sponsor key={idx} {...props} />
                ))}
              </div>
            </div>
          </section>
        )}
      </main>
    </Layout>
  );
}

export default Home;

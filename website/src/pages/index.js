import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './index.module.css';

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
    image: `${baseUrl}img/ziverge.png`,
    imageAlt: 'Ziverge',
    imageLink: 'https://ziverge.com',
    content: 'Brilliant solutions for innovative companies'
  },
  {
    image: `${baseUrl}img/scalac.svg`,
    imageAlt: 'Scalac',
    imageLink: 'https://scalac.io/',
    content: 'Scale fast with Scala'
  },
  {
    image: `${baseUrl}img/septimal_mind.svg`,
    imageAlt: 'Septimal Mind',
    imageLink: 'https://7mind.io/',
    content: 'Inventing productivity'
  }
];

// How a single feature block is displayed 
function Feature(feature) {
  return (
    <div className='container col col--3'>
      <h2 className={styles.featureTitle}>{feature.title}</h2>
      <p className={styles.featureText}>{feature.content}</p>
    </div>
  );
}

// How a single sponsor block is being displayed 
function Sponsor(sponsor) {
  return (
    <div class='container col col--4'>
      <div className={styles.sponsorImageContainer}>
        <a href={`${sponsor.imageLink}`}>
          <img className={styles.sponsorImage} src={`${sponsor.image}`} alt={`${sponsor.imageAlt}`} />
        </a>
      </div>
      <p class={styles.sponsorText}>{sponsor.content}</p>
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
      description={`${siteConfig.tagline}`}
      image='/img/navbar_brand2x.png'>
      <header className={styles.headerContainer}>
        <div className={`container ${styles.headerInnerContainer} flex justify-center`}>
          <img className={styles.headerImage} src="/img/jumbotron_pattern.png" alt={`${siteConfig.title}`} />
          <div className={styles.headerDetailContainer}>
            <p className={styles.headerTagline}>{siteConfig.tagline}</p>
            <div className={styles.headerButtonContainer}>
              <Link
                className={`${styles.headerButton}`}
                to={useBaseUrl('overview/getting-started')}>
                Get Started
              </Link>
            </div>
          </div>
        </div >
      </header >
      <main>
        {features && features.length > 0 && (
          <section className={styles.featureSection}>
            <div class='container'>
              <div class='row'>
                {features.map((f, idx) => (
                  <Feature key={idx} {...f} />
                ))}
              </div>
            </div>
          </section>
        )}

        {sponsors && sponsors.length > 0 && (
          <section className={styles.sponsorSection}>
            <div class='container'>
              <div class='row'>
                {sponsors.map((s, idx) => (
                  <Sponsor key={idx} {...s} />
                ))}
              </div>
            </div>
          </section>
        )}
      </main>
    </Layout >
  );
}

export default Home;

import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';

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
  },
  {
    image: `https://files.softwaremill.com/logo/logo_vertical.png`,
    imageAlt: 'SoftwareMill',
    imageLink: 'https://softwaremill.com/',
    content: 'Custom software by true engineers'
  },
];

// How a single feature block is displayed 
function Feature(feature) {
  return (
    <div class='tw-container tw-text-center'>
      <h2 class='tw-text-red-700'>{feature.title}</h2>
      <p class='tw-text-lg tw-font-medium'>{feature.content}</p>
    </div>
  );
}

// How a single sponsor block is being displayed 
function Sponsor(sponsor) {
  return (
    <div class='tw-container'>
      <div class='tw-w-80 tw-h-40 tw-m-auto tw-flex tw-place-content-center'>
        <a class='tw-inline-flex' href={`${sponsor.imageLink}`}>
          <img class='tw-object-contain tw-object-scale-down' src={`${sponsor.image}`} alt={`${sponsor.imageAlt}`} />
        </a>
      </div>
      <p class='tw-text-center tw-text-lg tw-font-medium'>{sponsor.content}</p>
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
      description={`${siteConfig.tagLine}`}>
      <header class='tw-bg-black'>
        <div class='tw-mx-auto tw-relative'>
          <img class='tw-mx-auto' src="/img/jumbotron_pattern.png" alt={`${siteConfig.title}`} />
          <div class='tw-absolute tw-top-1/2 tw-w-full'>
            <p class='tw-text-center tw-text-white tw-text-lg tw-font-medium'>{siteConfig.tagline}</p>
            <div class='tw-container tw-mx-auto tw-flex tw-place-content-center'>
              <Link
                class='tw-inline-block tw-text-white tw-p-4 tw-border tw-rounded-lg tw-text-xl tw-font-bold hover:tw-text-white hover:tw-no-underline hover:tw-bg-gray-700'
                to={useBaseUrl('/getting_started')}>
                Get Started
              </Link>
            </div>
          </div>
        </div>
      </header>
      <main>
        {features && features.length > 0 && (
          <div class="tw-container tw-m-auto tw-mt-4">
            <div class='tw-grid lg:tw-grid-cols-4 md:tw-grid-cols-2 sm:tw-grid-cols-1 tw-gap-4'>
              {features.map((f, idx) => (
                <Feature key={idx} {...f} />
              ))}
            </div>
          </div>
        )}
        {sponsors && sponsors.length > 0 && (
          <div className="tw-container tw-m-auto tw-bg-gray-200 tw-rounded-xl">
            <div class='tw-grid lg:tw-grid-cols-2 sm:tw-grid-cols-1 tw-gap-4'>
              {sponsors.map((s, idx) => (
                <Sponsor key={idx} {...s} />
              ))}
            </div>
          </div>
        )}
      </main>
    </Layout >
  );
}

export default Home;

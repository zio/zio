import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

export default function Hero() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;

  return (
    <section className="flex flex-col gap-24 py-10">
      <div className="container flex flex-col items-center gap-6">
        <img
          className="h-20"
          src="/img/navbar_brand2x.png"
          alt={`${siteConfig.title}`}
        />

        <p className="w-full max-w-7xl text-center text-3xl font-black leading-tight md:text-5xl">
          Type-safe, composable asynchronous and concurrent programming for{' '}
          <span className="text-primary">Scala</span>
        </p>
      </div>
    </section>
  );
}

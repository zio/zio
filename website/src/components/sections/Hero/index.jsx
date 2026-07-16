import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Link from '@docusaurus/Link';
import { FaArrowRight, FaStar } from 'react-icons/fa6';

import RepoStats from '@site/src/components/ui/RepoStats';

export default function Hero() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;

  return (
    <section className="relative overflow-hidden py-16 md:py-24">
      {/* Soft, theme-aware red radial glow behind the hero content. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[420px]"
        style={{
          background:
            'radial-gradient(60% 100% at 50% 0%, rgba(220,38,38,0.14), transparent 70%)',
        }}
      />

      <div className="container flex flex-col items-center gap-6 text-center">
        <span className="eyebrow">Purely functional effect system</span>

        <img
          className="h-20 md:h-24"
          src="/img/navbar_brand2x.png"
          alt={`${siteConfig.title}`}
        />

        <h1 className="w-full max-w-5xl text-3xl leading-tight font-black tracking-tight md:text-5xl">
          Type-safe, composable{' '}
          <span className="gradient-text">asynchronous</span> and concurrent
          programming for Scala
        </h1>

        <p className="max-w-2xl text-lg text-zinc-600 dark:text-zinc-400">
          Build resilient, high-performance applications with a purely
          functional effect system — with rich concurrency, resource safety, and
          testability built in.
        </p>

        <div className="flex flex-wrap items-center justify-center gap-3">
          <Link
            className="hover:bg-primary-500 bg-primary flex items-center gap-2 rounded-full px-6 py-2.5 font-semibold text-white transition-colors hover:text-white hover:no-underline"
            to="/overview/getting-started"
          >
            <span>Get Started</span>
            <FaArrowRight />
          </Link>

          <Link
            className="hover:border-primary hover:text-primary flex items-center gap-2 rounded-full border border-zinc-300 px-6 py-2.5 font-semibold text-zinc-800 transition-colors hover:no-underline dark:border-zinc-700 dark:text-zinc-100"
            href="https://github.com/zio/zio"
          >
            <FaStar className="text-accent" />
            <span>Star on GitHub</span>
          </Link>
        </div>

        <RepoStats />
      </div>
    </section>
  );
}

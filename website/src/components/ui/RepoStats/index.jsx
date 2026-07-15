import React, { useEffect, useState } from 'react';
import { repoStats } from './data';

function formatStars(n) {
  if (n >= 1000) {
    return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`;
  }
  return `${n}`;
}

/**
 * Trust badge row for the hero: live star count with a static fallback, plus
 * static contributor count and latest version. The GitHub call is
 * unauthenticated and best-effort — any failure (offline, rate limit) silently
 * keeps the baked-in fallback.
 */
export default function RepoStats() {
  const [stars, setStars] = useState(repoStats.stars);

  useEffect(() => {
    let active = true;
    fetch(`https://api.github.com/repos/${repoStats.owner}/${repoStats.repo}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (active && data && typeof data.stargazers_count === 'number') {
          setStars(data.stargazers_count);
        }
      })
      .catch(() => {
        /* keep fallback */
      });
    return () => {
      active = false;
    };
  }, []);

  const items = [
    { value: formatStars(stars), label: 'stars' },
    { value: repoStats.contributors, label: 'contributors' },
    { value: repoStats.version, label: 'latest' },
  ];

  return (
    <div className="flex flex-wrap items-center justify-center gap-x-8 gap-y-2 text-sm text-zinc-500 dark:text-zinc-400">
      {items.map((item) => (
        <span key={item.label}>
          <span className="font-bold text-zinc-900 dark:text-zinc-100">
            {item.value}
          </span>{' '}
          {item.label}
        </span>
      ))}
    </div>
  );
}

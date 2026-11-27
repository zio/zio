import React, { useEffect, useMemo, useState } from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import { useHistory } from '@docusaurus/router';
import data from './ecosystem.json';
import styles from './styles.module.css';

// The library cards of /ecosystem/: every official, community and compatible library, and every ZIO Blocks block
// (official too), grouped by the categories that zio.dev/ecosystem and zioblocks.com/reference share, with a filter box.
// The data is ecosystem.json, which scripts/generate-ecosystem-libraries.js writes.

const ORDER_KEY = 'eco-cards-order';
const KIND_LABEL = { official: 'Official', community: 'Community', compatible: 'Compatible' };
const isBlock = (card) => card.family === 'zio-blocks';
const isExternal = (href) => /^https?:/.test(href);
const isApiPage = (href) => /\/api\/$/.test(href);
// "ZIO Kafka" sorts under K, next to the community libraries for Kafka.
const sortKey = (name) => name.replace(/^ZIO\s+(2\.x\s+)?/i, '').toLowerCase();
const byName = (a, b) => sortKey(a.name).localeCompare(sortKey(b.name));

const FILTERS = [
  { id: 'official', label: 'Official', test: (c) => c.kind === 'official' },
  { id: 'blocks', label: 'Official: ZIO Blocks only', test: isBlock },
  { id: 'community', label: 'Community', test: (c) => c.kind === 'community' },
  { id: 'compatible', label: 'Compatible', test: (c) => c.kind === 'compatible' },
  { id: 'api', label: 'Has API reference', test: (c) => c.api },
];

const GROUPS = [...data.categories, { id: 'compatible', label: 'Compatible libraries' }];

function matches(card, words) {
  const text = [card.name, card.description, card.artifact || '', label(card.category), isBlock(card) ? 'zio blocks' : '']
    .join(' ')
    .toLowerCase();
  return words.every((word) => text.includes(word));
}

function label(category) {
  return (GROUPS.find((g) => g.id === category) || { label: '' }).label;
}

// Name matches first, then category, then description.
function rank(card, query) {
  const name = card.name.toLowerCase();
  if (sortKey(card.name).startsWith(query) || name.startsWith(query)) return 0;
  if (name.includes(query)) return 1;
  if (label(card.category).toLowerCase().includes(query)) return 2;
  return 3;
}

function Text({ children }) {
  // Descriptions use `code` spans.
  return children.split(/(`[^`]+`)/).map((part, i) =>
    part.startsWith('`') && part.endsWith('`') ? <code key={i}>{part.slice(1, -1)}</code> : part
  );
}

function CardLink({ href, className, children }) {
  if (isExternal(href) || isApiPage(href)) return <a className={className} href={href}>{children}</a>;
  return <Link className={className} to={href}>{children}</Link>;
}

function Name({ name }) {
  const zio = name.match(/^(ZIO\s+(?:2\.x\s+)?)(.*)$/);
  return zio ? <><span className={styles.prefix}>{zio[1]}</span>{zio[2]}</> : name;
}

// The website a card belongs to: ZIO Blocks and ZIO HTTP have their own sites; the other official libraries are on
// zio.dev. Official links to other sites (ZIO IntelliJ on GitHub) get no tag.
function siteOf(card) {
  if (isBlock(card)) return 'zioblocks.com';
  if (card.href.startsWith('https://ziohttp.com')) return 'ziohttp.com';
  if (card.kind === 'official' && card.href.startsWith('/')) return 'zio.dev';
  return null;
}

function Card({ card, first }) {
  const block = isBlock(card);
  const site = siteOf(card);
  return (
    <li className={clsx(styles.card, block && styles.block, first && styles.first)}>
      <div className={styles.top}>
        {block && <img className={styles.blockIcon} src="/img/ecosystem/zio-blocks-icon.svg" alt="" />}
        <CardLink className={styles.name} href={card.href}>
          <Name name={card.name} />
          {isExternal(card.href) && !block && <span className={styles.external} aria-label="(external site)"> ↗</span>}
        </CardLink>
        <span className={styles.tags}>
          {first ? (
            <span className={styles.enter} aria-hidden="true">Enter ↵</span>
          ) : (
            <span className={clsx(styles.kind, styles[card.kind])}>{KIND_LABEL[card.kind]}</span>
          )}
          {site && <span className={clsx(styles.kind, styles.siteTag)}>{site}</span>}
        </span>
      </div>
      <p className={styles.description}><Text>{card.description}</Text></p>
      <div className={styles.footer}>
        {block ? (
          <>
            <span className={styles.family}>ZIO Blocks · <code>{card.artifact}</code></span>
            <span>{card.platforms.join(' · ')} · {card.scala === '3.x' ? 'Scala 3' : 'Scala 2.13 + 3'}</span>
          </>
        ) : (
          <>
            <span>{label(card.category)}</span>
            {card.api && <a className={styles.api} href={`${card.href}api/`}>API reference</a>}
          </>
        )}
      </div>
    </li>
  );
}

export default function EcosystemCards() {
  const history = useHistory();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState(null);
  const [order, setOrder] = useState('category');

  // The order is a per-reader preference; the server renders the default.
  useEffect(() => {
    try {
      if (window.localStorage.getItem(ORDER_KEY) === 'az') setOrder('az');
    } catch (e) {
      // Storage is blocked: keep the default.
    }
  }, []);
  const chooseOrder = (next) => {
    setOrder(next);
    try {
      window.localStorage.setItem(ORDER_KEY, next);
    } catch (e) {
      // Storage is blocked: the choice lasts for this page view.
    }
  };

  const q = query.trim().toLowerCase();
  const words = q.split(/\s+/).filter(Boolean);
  const active = FILTERS.find((f) => f.id === filter);
  const shown = useMemo(
    () => data.cards.filter((c) => (!active || active.test(c)) && matches(c, words)).sort(byName),
    [filter, q]
  );
  const ranked = q ? shown.map((c, i) => [rank(c, q), i, c]).sort((a, b) => a[0] - b[0] || a[1] - b[1]).map((x) => x[2]) : shown;

  const libraries = shown.filter((c) => !isBlock(c)).length;
  const blocks = shown.length - libraries;
  const summary = [
    libraries ? `${libraries} ${libraries === 1 ? 'library' : 'libraries'}` : '',
    blocks ? `${blocks} ${blocks === 1 ? 'block' : 'blocks'}` : '',
  ].filter(Boolean).join(' · ') || 'No matches';

  const open = (card) => {
    if (isExternal(card.href) || isApiPage(card.href)) window.location.assign(card.href);
    else history.push(card.href);
  };

  return (
    <div className={styles.cards}>
      <div className={styles.finder} role="search">
        <label htmlFor="eco-cards-q" className={styles.label}>Find a library or block</label>
        <input
          id="eco-cards-q"
          className={styles.input}
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setQuery('');
            if (e.key === 'Enter' && q && ranked.length > 0) open(ranked[0]);
          }}
          placeholder="Name or topic: kafka, jwt, avro, redis…"
          autoComplete="off"
          spellCheck="false"
          aria-describedby="eco-cards-hint"
        />
        <div className={styles.controls}>
          <div className={styles.chips} role="group" aria-label="Show only">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                type="button"
                className={clsx(styles.chip, f.id === 'blocks' && styles.chipBlocks)}
                aria-pressed={filter === f.id}
                onClick={() => setFilter(filter === f.id ? null : f.id)}
              >
                {f.label} <span className={styles.n}>{data.cards.filter(f.test).length}</span>
              </button>
            ))}
          </div>
          <div className={styles.order} role="group" aria-label="Order">
            {[['category', 'By category'], ['az', 'A–Z']].map(([id, text]) => (
              <button key={id} type="button" aria-pressed={order === id} disabled={!!q} onClick={() => chooseOrder(id)}>
                {text}
              </button>
            ))}
          </div>
          <span className={styles.count} role="status">{summary}</span>
        </div>
        <p id="eco-cards-hint" className={styles.hint}>
          Matches names, descriptions and categories, name matches first. Enter opens the first card; Esc clears.
        </p>
      </div>

      {shown.length === 0 ? (
        <p className={styles.empty}>Nothing matches “{query}”. Try the site search, or clear the box.</p>
      ) : q || order === 'az' ? (
        <ul className={styles.grid}>
          {ranked.map((card, i) => <Card key={card.href + card.name} card={card} first={!!q && i === 0} />)}
        </ul>
      ) : (
        GROUPS.map((group) => {
          const cards = shown.filter((c) => c.category === group.id);
          if (cards.length === 0) return null;
          const libs = cards.filter((c) => !isBlock(c)).length;
          const blks = cards.length - libs;
          return (
            <section key={group.id} className={styles.group} aria-labelledby={`eco-${group.id}`}>
              <h2 id={`eco-${group.id}`} className={styles.groupHead}>
                {group.label}
                <span className={styles.groupCount}>
                  {[libs ? `${libs} ${libs === 1 ? 'library' : 'libraries'}` : '', blks ? `${blks} ${blks === 1 ? 'block' : 'blocks'}` : '']
                    .filter(Boolean)
                    .join(' · ')}
                </span>
              </h2>
              <ul className={styles.grid}>
                {cards.map((card) => <Card key={card.href + card.name} card={card} first={false} />)}
              </ul>
            </section>
          );
        })
      )}
    </div>
  );
}

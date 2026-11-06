import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';

/** Responsive grid for ResourceCard / Banner children. */
export function CardGrid({ cols = 2, children }) {
  return (
    <div className={clsx(styles.grid, cols === 3 && styles.grid3)}>
      {children}
    </div>
  );
}

/**
 * A card. With `href`, the title is the link and the whole card is clickable
 * (stretched link), with `cta` shown as the call to action. Without `href`
 * the card is plain and its children may contain their own links.
 */
export function ResourceCard({ eyebrow, badge, badgeKind, title, href, cta, children }) {
  return (
    <div className={clsx(styles.card, href && styles.cardLink)}>
      {eyebrow || badge ? (
        <div className={styles.meta}>
          {eyebrow ? <span className={styles.eyebrow}>{eyebrow}</span> : <span />}
          {badge ? (
            <span className={clsx(styles.badge, badgeKind === 'paid' ? styles.badgePaid : styles.badgeFree)}>
              {badge}
            </span>
          ) : null}
        </div>
      ) : null}
      <h3 className={styles.title}>
        {href ? (
          <Link to={href} className={styles.titleLink}>
            {title}
          </Link>
        ) : (
          title
        )}
      </h3>
      {children ? <div className={styles.body}>{children}</div> : null}
      {href && cta ? (
        <span className={styles.cta} aria-hidden="true">
          {cta} &rarr;
        </span>
      ) : null}
    </div>
  );
}

/** Brand-tinted call-out panel. */
export function Banner({ title, children }) {
  return (
    <section className={styles.banner}>
      <h2 className={styles.bannerTitle}>{title}</h2>
      <div className={styles.body}>{children}</div>
    </section>
  );
}

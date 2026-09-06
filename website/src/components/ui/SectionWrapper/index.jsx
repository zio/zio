import React from 'react';
import styles from './styles.module.css';

export default function SectionWrapper({ eyebrow, title, subtitle, children }) {
  return (
    <section className="py-10">
      {title ? (
        <div className="container mb-10 flex flex-col items-center text-center">
          {eyebrow ? <span className="eyebrow mb-3">{eyebrow}</span> : null}
          <h2 className="section-title">{title}</h2>
          <div className="gradient-rule" />
        </div>
      ) : null}
      {subtitle ? (
        <div className="col col--12 text--center">
          <p className={styles.subtitle}>{subtitle}</p>
        </div>
      ) : null}
      {children}
    </section>
  );
}

import React from 'react';
import styles from './styles.module.css';

export default function SectionWrapper({ title, subtitle, children }) {
  return (
    <section className="py-10">
      {title ? (
        <div className="container mb-10">
          <h2 className="text-center text-4xl font-bold">{title}</h2>
        </div>
      ) : null}
      {subtitle ? (
          <div className="col col--12 text--center">
            <p className={styles.subtitle}>{subtitle}</p>
          </div>
        ): null}
      {children}
    </section>
  );
}

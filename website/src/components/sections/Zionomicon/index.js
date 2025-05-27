import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';

export default function HomepageZionomicon() {
  return (
    <section className={styles.zionomicon}>
      <div className={styles.wideContainer}>
        <div className="row">
          <div className="col col--6">
            <div className={styles.ziconContent}>
              <h2 className={clsx("sectionHeader", "text-4xl")}>Learn ZIO with Zionomicon</h2>
              <p className={styles.ziconSubtitle}>
                The comprehensive guide to building scalable applications with ZIO
              </p>
              <p>
                Zionomicon stands as the comprehensive guide to mastering ZIO—the game-changing
                library that's revolutionizing how developers build robust Scala applications.
                It takes you from the fundamentals to advanced topics, teaching you how to build
                concurrent, resilient, and testable applications.
              </p>
              <p>
                In Zionomicon, you'll master:
              </p>
              <ul>
                <li>Modeling complex business logic using ZIO's effect system</li>
                <li>Error handling and resource management with ZIO</li>
                <li>Concurrent and asynchronous programming patterns</li>
                <li>Building predictable and testable applications</li>
                <li>Structured dependency injection using ZIO's layer system</li>
                <li>And much more to explore!</li>
              </ul>
              <div className={styles.buttonContainer}>
                <Link
                  className="button button--primary button--lg"
                  to="https://www.zionomicon.com"
                  target="_blank">
                  Get the Book for Free
                </Link>
              </div>
            </div>
          </div>
          <div className="col col--6">
            <div className={styles.ziconImageContainer}>
              <Link
                to="https://www.zionomicon.com"
                target="_blank">
                <img
                  src="img/zionomicon.png"
                  alt="Zionomicon Book Cover"
                  className={styles.ziconImage}
                />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
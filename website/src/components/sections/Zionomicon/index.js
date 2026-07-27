import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import { FaArrowRight } from 'react-icons/fa6';
import styles from './styles.module.css';

import SectionWrapper from '@site/src/components/ui/SectionWrapper';

export default function HomepageZionomicon() {
  return (
    <SectionWrapper
      title="Learn ZIO with Zionomicon"
      subtitle="The comprehensive guide to building scalable applications with ZIO"
    >
      <div className="container">
        <div className={clsx('row', styles.ziconRow)}>
          <div className="col col--6">
            <div className={styles.ziconContent}>
              <p>
                Zionomicon stands as the comprehensive guide to mastering
                ZIO—the game-changing library that's revolutionizing how
                developers build robust Scala applications. It takes you from
                the fundamentals to advanced topics, teaching you how to build
                concurrent, resilient, and testable applications.
              </p>
              <p>In Zionomicon, you'll master:</p>
              <ul>
                <li>
                  Modeling complex business logic using ZIO's effect system
                </li>
                <li>Error handling and resource management with ZIO</li>
                <li>Concurrent and asynchronous programming patterns</li>
                <li>Building predictable and testable applications</li>
                <li>
                  Structured dependency injection using ZIO's layer system
                </li>
                <li>And much more to explore!</li>
              </ul>
              <div className={styles.buttonContainer}>
                <Link
                  className="hover:bg-primary-500 bg-primary inline-flex items-center gap-2 rounded-full px-6 py-2.5 font-semibold text-white transition-colors hover:text-white hover:no-underline"
                  to="https://www.zionomicon.com"
                  target="_blank"
                >
                  <span>Get the Book for Free</span>
                  <FaArrowRight />
                </Link>
              </div>
            </div>
          </div>
          <div className="col col--6">
            <div className={styles.ziconImageContainer}>
              <Link to="https://www.zionomicon.com" target="_blank">
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
    </SectionWrapper>
  );
}

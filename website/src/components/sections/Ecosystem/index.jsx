import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import ThemedImage from '@theme/ThemedImage';
import { FaArrowRight, FaArrowUpRightFromSquare } from 'react-icons/fa6';
import styles from './styles.module.css';
import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { ecosystemProjects } from './data';

// Same pill as the hero and code-showcase primary buttons.
const PILL_BUTTON =
  'hover:bg-primary-500 bg-primary inline-flex items-center gap-2 rounded-full px-6 py-2.5 font-semibold text-white transition-colors hover:text-white hover:no-underline';

function ProjectIcon({ icon, name }) {
  if (typeof icon === 'object') {
    return (
      <ThemedImage
        alt={`${name} logo`}
        sources={{ light: icon.light, dark: icon.dark }}
        className={styles.ecosystemCardIconImg}
      />
    );
  }
  if (typeof icon === 'string' && icon.startsWith('/')) {
    return <img src={icon} alt={`${name} logo`} className={styles.ecosystemCardIconImg} />;
  }
  if (typeof icon === 'function') {
    const Icon = icon;
    return <Icon aria-hidden="true" />;
  }
  return icon;
}

// External links get an "open in new tab" icon; internal ones an arrow.
function LearnMore({ to }) {
  const external = /^https?:\/\//.test(to);
  return (
    <Link
      className={PILL_BUTTON}
      to={to}
      {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
    >
      <span>Learn More</span>
      {external ? (
        <FaArrowUpRightFromSquare aria-hidden="true" />
      ) : (
        <FaArrowRight aria-hidden="true" />
      )}
    </Link>
  );
}

export default function Ecosystem({ eyebrow, title, subtitle, children}) {
  // Separate ZIO Blocks from other projects (featured project)
  const featuredProject = ecosystemProjects.find(p => p.name === 'ZIO Blocks');
  const otherProjects = ecosystemProjects.filter(p => p.name !== 'ZIO Blocks');

  return (
    <SectionWrapper eyebrow={eyebrow} title={title} subtitle={subtitle} >
      <div className={styles.wideContainer}>
        {/* Featured project in its own row */}
        {featuredProject && (
          <div className={clsx('row', styles.ecosystemCards)}>
            <div className={clsx('col col--8 col--offset-2', styles.mainProjectCol)}>
              <div className={styles.ecosystemCard}>
                <div className={styles.ecosystemCardHeader}>
                  <h3>
                    <ThemedImage
                      alt={featuredProject.name}
                      sources={{
                        light: useBaseUrl(featuredProject.logo.light),
                        dark: useBaseUrl(featuredProject.logo.dark),
                      }}
                      className={styles.ecosystemCardLogo}
                    />
                  </h3>
                </div>
                <p className={styles.ecosystemCardDescription}>{featuredProject.description}</p>
                <ul className={styles.ecosystemCardFeatures}>
                  {featuredProject.features.map((feature, fidx) => (
                    <li key={fidx}>{feature}</li>
                  ))}
                </ul>
                <div className={styles.ecosystemCardFooter}>
                  <LearnMore to={featuredProject.link} />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Other projects in a grid */}
        <div className={clsx('row', styles.ecosystemCards)}>
          {otherProjects.map((project, idx) => (
            <div key={idx} className={clsx('col col--4', styles.ecosystemCardCol)}>
              <div className={styles.ecosystemCard}>
                <div className={styles.ecosystemCardHeader}>
                  <div className={styles.ecosystemCardIcon}>
                    <ProjectIcon icon={project.icon} name={project.name} />
                  </div>
                  <h3>{project.name}</h3>
                </div>
                <p className={styles.ecosystemCardDescription}>{project.description}</p>
                <ul className={styles.ecosystemCardFeatures}>
                  {project.features.map((feature, fidx) => (
                    <li key={fidx}>{feature}</li>
                  ))}
                </ul>
                <div className={styles.ecosystemCardFooter}>
                  <LearnMore to={project.link} />
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-10 flex justify-center">
          <Link className={PILL_BUTTON} to="/ecosystem/">
            <span>Browse all libraries</span>
            <FaArrowRight aria-hidden="true" />
          </Link>
        </div>
      </div>
    </SectionWrapper>
  );
}

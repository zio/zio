import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';
import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { ecosystemProjects } from './data';

function ProjectIcon({ icon, name }) {
  if (typeof icon === 'string' && icon.startsWith('/')) {
    return <img src={icon} alt={`${name} logo`} className={styles.ecosystemCardIconImg} />;
  }
  return icon;
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
                  <div className={styles.ecosystemCardIcon}>
                    <ProjectIcon icon={featuredProject.icon} name={featuredProject.name} />
                  </div>
                  <h3>{featuredProject.name}</h3>
                </div>
                <p className={styles.ecosystemCardDescription}>{featuredProject.description}</p>
                <ul className={styles.ecosystemCardFeatures}>
                  {featuredProject.features.map((feature, fidx) => (
                    <li key={fidx}>{feature}</li>
                  ))}
                </ul>
                <div className={styles.ecosystemCardFooter}>
                  <Link
                    className={clsx('button button--outline button--primary', styles.ecosystemCardButton)}
                    to={featuredProject.link}>
                    Learn More
                  </Link>
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
                  <Link
                    className={clsx('button button--outline button--primary', styles.ecosystemCardButton)}
                    to={project.link}>
                    Learn More
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </SectionWrapper>
  );
}
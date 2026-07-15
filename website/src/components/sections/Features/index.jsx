import React from 'react';
import { FaCheck } from 'react-icons/fa6';

import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { features } from './data';

export default function Features() {
  return (
    <SectionWrapper eyebrow="Why ZIO" title="Features">
      <div className="container grid list-none grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
        {features.map((item, idx) => (
          <div
            key={`features-${idx}`}
            className="card-modern flex flex-col gap-4 p-6"
          >
            <div className="card-icon-tile">
              <FaCheck />
            </div>
            <h2 className="text-xl font-bold">{item.title}</h2>
            <p className="text-zinc-600 dark:text-zinc-400">{item.content}</p>
          </div>
        ))}
      </div>
    </SectionWrapper>
  );
}

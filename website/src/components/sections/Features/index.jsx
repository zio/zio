import React from 'react';

import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { features } from './data';

export default function Features() {
  return (
    <SectionWrapper title="Features">
      <div className="container grid list-none grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
        {features.map((item, idx) => (
          <div
            key={`features-${idx}`}
            className="card-modern flex flex-col gap-4 p-6"
          >
            <div className="flex items-center gap-3">
              <div className="card-icon-tile shrink-0">
                <item.icon />
              </div>
              <h2 className="my-0 text-xl font-bold">{item.title}</h2>
            </div>
            <p className="text-zinc-600 dark:text-zinc-400">{item.content}</p>
          </div>
        ))}
      </div>
    </SectionWrapper>
  );
}

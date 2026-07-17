import React from 'react';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import CodeBlock from '@theme/CodeBlock';

import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { examples } from './data';

export default function CodeShowcase() {
  return (
    <SectionWrapper
      title="Show me the code"
      subtitle="Five everyday problems, solved the ZIO way"
    >
      <div className="container">
        <div className="mx-auto max-w-3xl">
          <Tabs>
            {examples.map((example) => (
              <TabItem
                key={example.value}
                value={example.value}
                label={example.label}
              >
                <CodeBlock language="scala">{example.code}</CodeBlock>
                <p className="text-zinc-600 dark:text-zinc-400">
                  {example.takeaway}
                </p>
              </TabItem>
            ))}
          </Tabs>
        </div>
      </div>
    </SectionWrapper>
  );
}

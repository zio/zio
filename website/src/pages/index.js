import React from 'react';
import Layout from '@theme/Layout';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

function Home() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;
  return (
    <Layout
      title={`${siteConfig.title}`}
      description="Composable, functional integration flows">
      <div className="container">
        <h1>Hello Andreas!</h1>
      </div>
    </Layout>
  );
}

export default Home;

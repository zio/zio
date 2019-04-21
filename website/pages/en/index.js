/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require('react');

const CompLibrary = require('../../core/CompLibrary.js');

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

const features = [
  {
    name: 'High-Performance',
    description: 'Build scalable applications with 100x the performance of Scala’s Future'
  },
  {
    name: '',
    description: ''
  },
];

class Description extends React.Component {
  render() {
    return (
      <div className='welcome'>
        <h2>Welcome to ZIO</h2>
        <p>
          <div> 
            ZIO is a zero-dependency Scala library for asynchronous and concurrent programming.
          </div>
          <div>
            Powered by highly-scalable, non-blocking fibers that never waste or leak resources, ZIO lets you build scalable, resilient, and reactive applications that meet the needs of your business.
          </div>
        </p>
        <div> 
            <ul>
              <li><strong>High-performance.</strong> Build scalable applications with 100x the performance of Scala’s Future</li>
              <li><strong>Type-safe.</strong> Use the full power of the Scala compiler to catch bugs at compile time.</li>
              <li><strong>Concurrent.</strong> Easily build concurrent apps without deadlocks, race conditions, or complexity</li>
              <li><strong>Asynchronous.</strong> Write sequential code that looks the same whether it’s asynchronous or synchronous.</li>
              <li><strong>Resource-safe.</strong> Build apps that never leak resources (including threads!), even when they fail.</li>
              <li><strong>Testable.</strong> Inject test services into your app for fast, deterministic, and type-safe testing.</li>
              <li><strong>Resilient.</strong> Build apps that never lose errors, and which respond to failure locally and flexibly.</li>
              <li><strong>Functional.</strong> Rapidly compose solutions to complex problems from simple building blocks.</li>
            </ul>
        </div>
        <div>
            To learn more about how ZIO can help you accomplish the impossible, see <a href="./docs/getting_started.html">Getting Started</a> and <a href="./docs/overview/index.html">Overview</a>.
        </div>
      </div>
    );
  }
}

class HomeSplash extends React.Component {
  render() {
    const {siteConfig, language = ''} = this.props;
    const {baseUrl, docsUrl} = siteConfig;
    const docsPart = `${docsUrl ? `${docsUrl}/` : ''}`;
    const langPart = `${language ? `${language}/` : ''}`;
    const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

    const SplashContainer = props => (
      <div className="homeContainer">
        <div className="homeSplashFade">
          <div className="wrapper homeWrapper">{props.children}</div>
        </div>
      </div>
    );
    const ProjectTitle = () => (
      <h2 className="projectTitle">
        {siteConfig.title}
        <small>{siteConfig.tagline}</small>
      </h2>
    );

    const PromoSection = props => (
      <div className="section promoSection">
        <div className="promoRow">
          <div className="pluginRowBlock">{props.children}</div>
        </div>
      </div>
    );

    const Button = props => (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={props.href} target={props.target}>
          {props.children}
        </a>
      </div>
    );

    return (
      <SplashContainer>
        <div className="inner">
          <ProjectTitle siteConfig={siteConfig} />
          <PromoSection>
            <Button href="./docs/getting_started.html">Getting started</Button>
          </PromoSection>
        </div>
      </SplashContainer>
    );
  }
}

class Index extends React.Component {
  render() {
    const {config: siteConfig, language = ''} = this.props;
    const {baseUrl} = siteConfig;

    const Block = props => (
      <Container
        padding={['bottom', 'top']}
        id={props.id}
        background={props.background}>
        <GridBlock
          align="center"
          contents={props.children}
          layout={props.layout}
        />
      </Container>
    );
    return (
      <div>
        <HomeSplash siteConfig={siteConfig} language={language} />
        <div className="mainContainer">
          <Description />
        </div>
      </div>
    );
  }
}

module.exports = Index;

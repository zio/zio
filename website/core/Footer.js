/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Gitter from 'react-sidecar';

const React = require('react');

class Footer extends React.Component {
  docUrl(doc, language) {
    const baseUrl = this.props.config.baseUrl;
    const docsUrl = this.props.config.docsUrl;
    const docsPart = `${docsUrl ? `${docsUrl}/` : ''}`;
    const langPart = `${language ? `${language}/` : ''}`;
    return `${baseUrl}${docsPart}${langPart}${doc}`;
  }

  pageUrl(doc, language) {
    const baseUrl = this.props.config.baseUrl;
    return baseUrl + (language ? `${language}/` : '') + doc;
  }

  render() {
    return (
      <footer className="nav-footer" id="footer">
        <section className="sitemap">
          <a href={this.props.config.baseUrl} className="nav-home">
            {this.props.config.footerIcon && (
              <img
                src={this.props.config.baseUrl + this.props.config.footerIcon}
                alt={this.props.config.title}
              />
            )}
          </a>
          <div>
            <h5>GitHub</h5>
            <a href="https://github.com/zio/zio"><img src="https://img.shields.io/github/stars/zio/zio?style=social" alt="github"/></a>
          </div>
          <div>
              <h5>Chat with us on Discord</h5>
              <a href="https://discord.gg/2ccFBr4"><img src="https://img.shields.io/discord/629491597070827530?logo=discord&style=social" alt="discord"/></a>
          </div>
          <div>
              <h5>Follow us on Twitter</h5>
              <a href="https://twitter.com/zioscala"><img src="https://img.shields.io/twitter/follow/zioscala?label=Follow&style=social" alt="twitter"/></a>
          </div>
          <div>
            <h5>Additional resources</h5>
            <a
              href="https://javadoc.io/doc/dev.zio/zio_2.12/">
              Scaladoc of zio
            </a>
          </div>
          <div>
              <a href="https://www.netlify.com"><img src="https://www.netlify.com/img/global/badges/netlify-color-accent.svg" alt="Deploys by Netlify"/></a>
          </div>
        </section>
        <section className="copyright">{this.props.config.copyright}</section>
      </footer>
    );
  }
}

module.exports = Footer;

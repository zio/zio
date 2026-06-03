function googleFontsPlugin() {
  return {
    name: 'docusaurus-google-fonts',
    injectHtmlTags() {
      const fonts = [{ family: 'Inter', weight: '400..900' }];
      const fontsLink = getFontsLink(fonts);

      return {
        headTags: [
          {
            tagName: 'link',
            attributes: {
              rel: 'preconnect',
              href: 'https://fonts.googleapis.com',
            },
          },
          {
            tagName: 'link',
            attributes: {
              rel: 'preconnect',
              href: 'https://fonts.gstatic.com',
              crossorigin: true,
            },
          },
          fontsLink,
        ],
      };
    },
  };
}

function getFontsLink(fonts) {
  const fontsParams = fonts.map((font) => {
    const fontWeight = !font.weight ? '' : `:wght@${font.weight}`;

    return `family=${font.family}${fontWeight}`;
  });

  const fontHref = `https://fonts.googleapis.com/css2?${fontsParams.join('&')}&display=swap`;

  return {
    tagName: 'link',
    attributes: {
      rel: 'stylesheet',
      href: fontHref,
    },
  };
}

module.exports = googleFontsPlugin;

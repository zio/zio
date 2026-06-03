function googleFontsPlugin() {
  return {
    name: 'docusaurus-google-fonts',
    injectHtmlTags() {
      const fonts = [{ family: 'Inter', weight: '400..900' }];
      const fontsLinks = getFontsLink(fonts);

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
          ...fontsLinks,
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

  // Use preload for faster font loading while display=swap prevents invisible text during load.
  // Preload initiates the download early; display=swap allows the page to render immediately
  // with fallback fonts and swap to the loaded font when ready. This balances performance and UX.
  return [
    {
      tagName: 'link',
      attributes: {
        rel: 'preload',
        href: fontHref,
        as: 'style',
      },
    },
    {
      tagName: 'link',
      attributes: {
        rel: 'stylesheet',
        href: fontHref,
      },
    },
  ];
}

module.exports = googleFontsPlugin;

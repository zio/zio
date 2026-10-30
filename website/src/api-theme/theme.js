// Replaces Scaladoc's scripts/theme.js in build/api (scripts/theme-api.mjs copies it there). Scaladoc loads that file
// early in <head>, so the colour mode is applied before the first paint, as Scaladoc's own version did.
(function () {
  // Colour mode, shared with the rest of the site: Docusaurus keeps it in localStorage "theme" ("light" or "dark").
  // docusaurus.config.js leaves colorMode at its defaults (light, not following the OS), so the docs toggle switches
  // between light and dark, and a page with nothing stored is light. The API pages do the same.
  var KEY = 'theme';
  var LABELS = { light: 'light mode', dark: 'dark mode' };

  function readMode() {
    try {
      return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light';
    } catch (e) {
      return 'light';
    }
  }

  function writeMode(mode) {
    try {
      localStorage.setItem(KEY, mode);
    } catch (e) {}
  }

  var mode = readMode();

  function apply() {
    var dark = mode === 'dark';
    var root = document.documentElement;
    // Scaladoc's stylesheets switch on the theme-dark class; data-theme mirrors what Docusaurus sets.
    root.classList.toggle('theme-dark', dark);
    root.setAttribute('data-theme', mode);
    root.setAttribute('data-theme-choice', mode);
    var toggles = document.querySelectorAll('[data-color-mode-toggle]');
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].title = LABELS[mode];
      toggles[i].setAttribute('aria-label', 'Switch between dark and light mode (currently ' + LABELS[mode] + ')');
    }
  }

  apply();
  // Another tab (a docs page, say) changed the mode.
  window.addEventListener('storage', function (e) {
    if (e.key === KEY) {
      mode = readMode();
      apply();
    }
  });

  window.addEventListener('DOMContentLoaded', function () {
    // The window scrolls instead of #main (api.css), but Scaladoc's ux.js still scrolls #main: to the #section in the
    // URL on load, and to the top after loading a page in place. Send those to the window. This listener is added
    // before ux.js adds its own, so it runs first; ux.js swaps #main's content, not the element, so it lasts.
    var main = document.getElementById('main');
    if (main)
      main.scrollTo = function () {
        window.scrollTo.apply(window, arguments);
      };
    apply();
    var toggles = document.querySelectorAll('[data-color-mode-toggle], #mobile-theme-toggle');
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].addEventListener('click', function () {
        mode = mode === 'dark' ? 'light' : 'dark';
        writeMode(mode);
        apply();
      });
    }
  });

  // The side menu's top level (the zio package) stays unfolded, like the categories of a docs sidebar. Scaladoc folds
  // the path of the page you leave on every in-place page load, top level included, so unfold it again after each one
  // ("dynamicPageLoad", which also fires once the menu is first drawn), except when the reader folded it.
  var foldedByReader = {};
  window.addEventListener('dynamicPageLoad', function () {
    var tops = document.querySelectorAll('#api-nav > .ni');
    for (var i = 0; i < tops.length; i++) {
      if (foldedByReader[tops[i].getAttribute('data-loc')]) continue;
      tops[i].classList.add('expanded');
      var arrow = tops[i].querySelector(':scope > .nh > button.ar');
      if (arrow) arrow.classList.add('expanded');
    }
  });

  document.addEventListener(
    'click',
    function (e) {
      var target = e.target instanceof Element ? e.target : null;

      // Remember a top-level package the reader folds or unfolds (this runs before Scaladoc toggles it).
      var arrow = target && target.closest('#api-nav > .ni > .nh > button.ar');
      if (arrow) {
        var top = arrow.parentElement.parentElement;
        foldedByReader[top.getAttribute('data-loc')] = top.classList.contains('expanded');
      }
      // Scaladoc loads every same-origin link in place, swapping in the fetched page's #main. The navbar's site links
      // (/reference/, /blog) are not Scaladoc pages, so stop the click before it reaches Scaladoc's handler on
      // the link itself (this listener runs first, in the capture phase) and let the browser load them normally.
      if (target && target.closest('#header a, .za-mobile-links a')) e.stopPropagation();

      // The "Onboard your agent" button, as on the rest of the site (src/components/ui/OnboardAgentButton): copy the
      // prompt and show a check for two seconds, or offer it in a dialog when the clipboard is unavailable.
      var button = target && target.closest('[data-onboard-prompt]');
      if (!button) return;
      var prompt = button.getAttribute('data-onboard-prompt');
      var copied = function () {
        button.classList.add('za-onboard--copied');
        clearTimeout(button._zaTimer);
        button._zaTimer = setTimeout(function () {
          button.classList.remove('za-onboard--copied');
        }, 2000);
      };
      var legacy = function () {
        var ok = false;
        try {
          var ta = document.createElement('textarea');
          ta.value = prompt;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.focus();
          ta.select();
          ok = document.execCommand('copy');
          document.body.removeChild(ta);
        } catch (err) {}
        if (ok) copied();
        else window.prompt('Copy this prompt for your coding agent:', prompt);
      };
      if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(prompt).then(copied, legacy);
      else legacy();
    },
    true,
  );
})();

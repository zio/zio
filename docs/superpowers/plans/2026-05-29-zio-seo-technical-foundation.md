# ZIO Website SEO Technical Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement technical SEO infrastructure for zio.dev — robots.txt, JSON-LD structured data, sitemap verification, and canonical URL configuration.

**Architecture:** Four independent configuration layers: (1) Static robots.txt file for crawler directives, (2) Custom Docusaurus theme component injecting JSON-LD schemas (Organization, Website, FAQ), (3) Verification that sitemap generation works, (4) Verification that canonical URLs are auto-configured. All changes are non-breaking configuration additions.

**Tech Stack:** Docusaurus 2.x, React/TypeScript (for theme component), JSON-LD structured data, Google Search Console

---

## File Structure

**Files to create:**
- `/website/static/robots.txt` — Crawler directives and sitemap reference
- `/website/src/components/SEOSchemas/OrganizationSchema.tsx` — Organization + Website schema component

**Files to modify:**
- `/website/src/theme/Root.tsx` — Inject schema components into every page
- `/website/docusaurus.config.js` — Verify sitemap plugin and canonical URL settings

**Deferred:**
- `/website/src/components/SEOSchemas/FAQSchema.tsx` — FAQ schema component (deferred to future work, see Task 3)

**No test files needed** — SEO schemas are verified with Google's validation tools, not unit tests

---

## Task 1: Create robots.txt

**Files:**
- Create: `/website/static/robots.txt`

- [ ] **Step 1: Create the robots.txt file with crawler directives**

Create `/website/static/robots.txt` with the following content:

```
# Allow all crawlers by default
User-agent: *
Allow: /
Disallow: /search
Disallow: /.docusaurus
Disallow: /admin
Disallow: /api/
Disallow: *?version=
Allow: /static/

# Sitemap reference for discovery
Sitemap: https://zio.dev/sitemap.xml

# Rate limit overly aggressive crawlers
# Note: Crawl-delay is honored by Bing, Yandex, and some third-party bots,
# but not by Googlebot. Use for managing load from non-Google crawlers.
User-agent: AhrefsBot
User-agent: SemrushBot
User-agent: DotBot
Crawl-delay: 10
```

Explanation:
- `Allow: /` — permits crawling the entire site
- `Disallow: /search`, `/.docusaurus`, `/admin` — blocks internal/non-indexable paths
- `Disallow: /api/` — prevents indexing API endpoints (if any added in future)
- `Disallow: *?version=` — blocks versioned URL parameters to prevent duplicate content (e.g., `?version=1.0`)
- `Allow: /static/` — explicitly allows static asset paths
- `Sitemap:` — tells crawlers where to find the sitemap
- `Crawl-delay: 10` — rate-limits aggressive bots (honored by Bing, Yandex, and third-party crawlers, but not Googlebot)

- [ ] **Step 2: Verify the file exists and is readable**

Run: `cat /website/static/robots.txt`
Expected output: The robots.txt content above, no errors.

- [ ] **Step 3: Commit the robots.txt file**

```bash
cd /home/milad/sources/scala/zio-2.x-modern
git add website/static/robots.txt
git commit -m "feat: add robots.txt for search engine crawler directives"
```

---

## Task 2: Create Organization and Website Schema Component

**Files:**
- Create: `/website/src/components/SEOSchemas/OrganizationSchema.tsx`

- [ ] **Step 1: Create the schema component directory**

Run: `mkdir -p /home/milad/sources/scala/zio-2.x-modern/website/src/components/SEOSchemas`

- [ ] **Step 2: Write the OrganizationSchema component**

Create `/website/src/components/SEOSchemas/OrganizationSchema.tsx`:

```typescript
import React from 'react';
import Head from '@docusaurus/Head';

export default function OrganizationSchema() {
  const organizationSchema = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'ZIO',
    url: 'https://zio.dev',
    logo: 'https://zio.dev/img/zio.png',
    description:
      'Type-safe, composable asynchronous and concurrent programming for Scala',
    sameAs: [
      'https://github.com/zio/zio',
      'https://twitter.com/zioscala',
      'https://discord.gg/2ccFBr4',
    ],
    contactPoint: {
      '@type': 'ContactPoint',
      contactType: 'Community Support',
      url: 'https://discord.gg/2ccFBr4',
    },
  };

  return (
    <Head>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationSchema) }}
      />
    </Head>
  );
}
```

Explanation:
- Single `<script>` tag for Organization schema
- `dangerouslySetInnerHTML` is the safe way to inject JSON-LD in React (not XSS risk with structured data)
- Use Docusaurus `Head` component to inject into page `<head>` (not body)
- Organization schema includes contact info and social profiles
- Note: Website schema (Sitelinks Search Box) is deprecated by Google and not included

- [ ] **Step 3: Verify the file exists and has valid syntax**

Run: `cat /website/src/components/SEOSchemas/OrganizationSchema.tsx`
Expected: File contents match the TypeScript code above, no errors.

- [ ] **Step 4: Commit the OrganizationSchema component**

```bash
git add website/src/components/SEOSchemas/OrganizationSchema.tsx
git commit -m "feat: add Organization and Website JSON-LD schema component"
```

---

## Task 3: Create FAQ Schema Component — DEFERRED

**Status:** Not implemented — deferred to future work

**Rationale:** Google's structured data policies require that FAQ schema content must exactly match visible content on the page. The current FAQ page contains only 2 questions, while any hardcoded FAQ schema would introduce fabricated content. Schemas that don't match visible content are treated as spammy and can result in:
- Rich result suppression in search results
- Manual actions in Google Search Console
- Loss of trust for other structured data on the domain

**Future Implementation:** FAQ schema can be implemented once:
1. FAQ page content is expanded to include the questions/answers to be marked up, OR
2. A parser is built to extract FAQ content from `docs/faq.md` and generate schema dynamically

**Reference:** [Google FAQ Schema Guidelines](https://developers.google.com/search/docs/advanced/structured-data/faqpage)

---

## Task 4: Inject Schemas into Root Theme Component

**Files:**
- Modify: `/website/src/theme/Root.tsx`

- [ ] **Step 1: Check if Root.tsx exists**

Run: `ls -la /website/src/theme/`
Expected: List of theme files. If `Root.tsx` doesn't exist, we'll create it.

- [ ] **Step 2: Read the existing Root.tsx (if it exists)**

Run: `cat /website/src/theme/Root.tsx` (if exists)
If the file exists, note its current content. If it doesn't exist, proceed to Step 3.

- [ ] **Step 3: Create or update Root.tsx to inject schemas**

If `Root.tsx` doesn't exist, create it at `/website/src/theme/Root.tsx`:

```typescript
import React from 'react';
import OrganizationSchema from '../components/SEOSchemas/OrganizationSchema';

export default function Root({ children }: { children: React.ReactNode }) {
  return (
    <>
      <OrganizationSchema />
      {children}
    </>
  );
}
```

If `Root.tsx` already exists, add the import and schema component to it, wrapping the existing children:

```typescript
// Add this import at the top
import OrganizationSchema from '../components/SEOSchemas/OrganizationSchema';

// Update the render to include the schema before children
```

Explanation:
- `Root.tsx` is the wrapper component that runs on every page (Docusaurus convention)
- `OrganizationSchema` is injected on ALL pages (includes both Organization and Website schemas)
- Schemas are injected into `<head>` automatically by Docusaurus Head component
- Note: FAQ schema is deferred (see Task 3)

- [ ] **Step 4: Verify Root.tsx is correct**

Run: `cat /website/src/theme/Root.tsx`
Expected: File contains the schema import and injection logic.

- [ ] **Step 5: Commit the Root.tsx changes**

```bash
git add website/src/theme/Root.tsx
git commit -m "feat: inject Organization schema into all pages"
```

---

## Task 5: Verify Sitemap Plugin Configuration

**Files:**
- Verify: `/website/docusaurus.config.js`

- [ ] **Step 1: Check if sitemap plugin is configured**

Run: `grep -A 5 "plugin-sitemap\|@docusaurus/plugin-sitemap" /website/docusaurus.config.js`
Expected: One of:
- Plugin is explicitly configured in the `plugins` array, OR
- Plugin is not mentioned (Docusaurus includes it by default)

- [ ] **Step 2: If plugin is not explicitly configured, add it**

If the grep command returned nothing, add the sitemap plugin to `/website/docusaurus.config.js`:

Find the `plugins` array (around line 263) and add this plugin configuration if not present:

```javascript
[
  '@docusaurus/plugin-sitemap',
  {
    changefreq: 'weekly',
    priority: 0.7,
    ignorePatterns: ['/tags/**'],
    filename: 'sitemap.xml',
  },
],
```

- [ ] **Step 3: Verify sitemap plugin is active**

Run: `grep -A 3 "@docusaurus/plugin-sitemap" /website/docusaurus.config.js`
Expected: Plugin configuration is present.

- [ ] **Step 4: Commit the sitemap plugin configuration**

```bash
git add website/docusaurus.config.js
git commit -m "feat: configure @docusaurus/plugin-sitemap for search engine discovery"
```

---

## Task 6: Verify Canonical URL Configuration

**Files:**
- Verify: `/website/docusaurus.config.js`

- [ ] **Step 1: Check trailingSlash setting**

Run: `grep "trailingSlash" /website/docusaurus.config.js`
Expected output: Either `trailingSlash: 'ignore'` or no output.

- [ ] **Step 2: If trailingSlash is not set to 'ignore', add it**

Open `/website/docusaurus.config.js` and ensure the top-level config object has:

```javascript
const config = {
  title: 'ZIO',
  tagline: '...',
  url: 'https://zio.dev',
  baseUrl: '/',
  trailingSlash: 'ignore',  // ADD THIS LINE if not present
  onBrokenLinks: 'warn',
  // ... rest of config
};
```

Explanation:
- `trailingSlash: 'ignore'` prevents duplicate pages (`/page` vs `/page/`)
- Docusaurus already auto-generates canonical URLs; this setting ensures they're correct

- [ ] **Step 3: Verify the setting is present**

Run: `grep -A 2 "url: 'https://zio.dev'" /website/docusaurus.config.js | head -5`
Expected: Lines show `baseUrl` and `trailingSlash: 'ignore'`.

- [ ] **Step 4: Commit the canonical URL configuration**

```bash
git add website/docusaurus.config.js
git commit -m "feat: set trailingSlash to 'ignore' for consistent canonical URLs"
```

---

## Task 7: Build and Test SEO Configuration

**Files:**
- Build: `/website/build/` (generated output)

- [ ] **Step 1: Build the website locally**

Run:
```bash
cd /website
npm run build
```

Expected: Build completes without errors. You should see:
```
✓ Build finished in 45 seconds
build/ directory created
```

- [ ] **Step 2: Verify robots.txt is in the build output**

Run: `cat /website/build/robots.txt`
Expected: The robots.txt content we created in Task 1, exactly as written.

- [ ] **Step 3: Verify sitemap.xml was generated**

Run: `ls -lh /website/build/sitemap*.xml`
Expected: At least one sitemap file exists.

- [ ] **Step 4: Check sitemap contains pages from all versions**

Run: `grep -c "<loc>" /website/build/sitemap.xml`
Expected: A number greater than 100 (all documentation pages).

Also verify version paths are included: `grep "1.0.18" /website/build/sitemap.xml | head -3`
Expected: Multiple entries with `1.0.18` in the URL path.

- [ ] **Step 5: Verify Organization schema is injected into homepage**

Run: `grep -A 2 '"@type": "Organization"' /website/build/index.html`
Expected: JSON-LD organization schema found in the HTML.

- [ ] **Step 6: Verify FAQ schema is injected into FAQ page**

Run: `grep -A 2 '"@type": "FAQPage"' /website/build/faq/index.html`
Expected: JSON-LD FAQ schema found in FAQ page HTML.

- [ ] **Step 7: Verify canonical URLs are present**

Run: `grep "rel=\"canonical\"" /website/build/overview/getting-started/index.html`
Expected: Line like: `<link rel="canonical" href="https://zio.dev/overview/getting-started">`

- [ ] **Step 8: Commit the build artifacts (optional documentation)**

```bash
git add -A
git commit -m "test: verify SEO configuration in build output"
```

---

## Task 8: Validate with Google Tools

**Files:**
- No files created; external tool validation

- [ ] **Step 1: Test Organization schema with Google**

1. Go to https://search.google.com/test/rich-results
2. Enter the URL: `https://zio.dev/`
3. Wait for validation to complete
4. Expected: ✅ Valid — "Organization" schema detected

Take a screenshot of the validation result.

- [ ] **Step 2: Test FAQ schema with Google**

1. Go to https://search.google.com/test/rich-results
2. Enter the URL: `https://zio.dev/faq`
3. Wait for validation
4. Expected: ✅ Valid — "FAQ" schema detected (will show FAQ rich snippet preview)

Take a screenshot.

- [ ] **Step 3: Test robots.txt with Google**

1. Go to Google Search Console (https://search.google.com/search-console)
2. In "Settings" → "Crawl stats", verify Google can access your site
3. In "Settings" → "Sitemaps", add `https://zio.dev/sitemap.xml` if not present
4. Expected: Sitemap shows as "Submitted" and crawl stats show normal activity

- [ ] **Step 4: Verify mobile-friendly**

1. Go to https://search.google.com/test/mobile-friendly
2. Enter `https://zio.dev/`
3. Expected: ✅ "Page is mobile friendly"

- [ ] **Step 5: Document validation results (internal)**

Document validation results in your notes for reference:

```
SEO Configuration Validation — Date

✅ robots.txt exists at https://zio.dev/robots.txt
✅ sitemap.xml generated with 450+ pages
✅ Organization schema validates (Google Rich Results)
✅ Canonical URLs present on all pages
✅ Mobile-friendly test passes
✅ Google Search Console can crawl site

Note: FAQ schema deferred (see Task 3)
```

**Note:** Do not commit a validation report file to the repository — it becomes stale quickly and serves no ongoing purpose. Use the design spec and implementation plan as documentation instead.

---

## Summary

**Total Tasks:** 8 (Task 3 deferred)  
**Total Files Created:** 2 (robots.txt, OrganizationSchema.tsx)  
**Total Files Modified:** 2 (Root.tsx, docusaurus.config.js)  
**Commits:** 5

**Key Deliverables:**
- ✅ Functional robots.txt with crawler directives
- ✅ Organization + Website JSON-LD schemas on all pages
- ✅ Verified sitemap generation for all versions
- ✅ Verified canonical URL configuration
- ✅ Validation with Google tools
- ⏳ FAQ schema (deferred to future work — see Task 3 rationale)

**Time Estimate:** 2-3 hours total (for implemented components)

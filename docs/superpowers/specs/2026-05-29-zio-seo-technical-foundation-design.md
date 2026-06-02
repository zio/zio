# ZIO Website SEO Technical Foundation — Design Spec

**Date:** 2026-05-29  
**Scope:** Technical SEO infrastructure configuration (no content changes)  
**Effort:** 2-3 hours  
**Goal:** Improve crawlability, indexing, and structured data for ZIO documentation site

---

## 1. Overview

Implement technical SEO foundation for zio.dev through configuration-only changes. Focus on making the site properly discoverable and indexable by search engines without modifying documentation content.

**Success Criteria:**
- `robots.txt` properly guides crawler behavior and rate-limits aggressive bots
- JSON-LD Organization schema present and validates correctly on all pages
- Sitemap generation verified and working for all versions
- Canonical URLs correctly configured across all pages
- No broken links or 404s in SEO-critical paths
- Website schema (Sitelinks Search Box) recognized as deprecated by Google, not implemented
- FAQ schema deferred to future work due to Google content-match policy

---

## 2. Architecture

Four independent but related configuration components:

```
ZIO Website SEO Foundation
├── robots.txt (crawler directives)
├── Structured Data (JSON-LD schemas)
├── Sitemap Management (discovery)
└── Canonical URLs (deduplication)
```

Each component runs independently; configuration changes are applied at build/deploy time.

---

## 3. Implementation Components

### 3.1 robots.txt Configuration

**Location:** `/website/static/robots.txt`

**Purpose:** Guide search engine crawlers on what to index/disallow

**Configuration:**
```
User-agent: *
Allow: /
Disallow: /search
Disallow: /.docusaurus
Disallow: /admin
Disallow: /api/
Disallow: *?version=
Allow: /static/

Sitemap: https://zio.dev/sitemap.xml

# Crawl delay for overly aggressive bots
User-agent: AhrefsBot
Crawl-delay: 10
```

**Rationale:**
- Allow all crawlers to access documentation and blog
- Block search/internal paths that don't benefit from indexing
- Reference sitemap location for discovery
- Rate-limit known aggressive crawlers (optional, based on traffic)

**Docusaurus Integration:**
- Place `robots.txt` in `/website/static/` — Docusaurus automatically serves it
- Build process copies all static files to build output

---

### 3.2 JSON-LD Structured Data

**Location:** Custom Docusaurus theme component or plugin

**Purpose:** Provide machine-readable metadata for search engines (better SERP display, rich snippets)

**Schemas to Implement:**

#### 3.2.1 Organization Schema
```json
{
  "@context": "https://schema.org",
  "@type": "Organization",
  "name": "ZIO",
  "url": "https://zio.dev",
  "logo": "https://zio.dev/img/zio.png",
  "description": "Type-safe, composable asynchronous and concurrent programming for Scala",
  "sameAs": [
    "https://github.com/zio/zio",
    "https://twitter.com/zioscala",
    "https://discord.gg/2ccFBr4"
  ],
  "contactPoint": {
    "@type": "ContactPoint",
    "contactType": "Community Support",
    "url": "https://discord.gg/2ccFBr4"
  }
}
```

**Placement:** In page `<head>` (global, appears on all pages)

#### 3.2.2 Website Schema with Search Action — DEPRECATED

**Status:** Not implemented — feature retired by Google

**Rationale:** Google deprecated the Sitelinks Search Box feature on November 21, 2024. The `WebSite` schema with `SearchAction` markup no longer triggers any SERP feature and provides no SEO value. Including it is dead code that adds no benefit.

**Reference:** [Google Blog: Sitelinks Search Box Deprecation (October 2024)](https://developers.google.com/search/blog/2024/10/sitelinks-search-box)

#### 3.2.3 FAQ Schema (Deferred)

⚠️ **Status:** Not implemented in this release — deferred to future work

**Rationale:** Google's structured data policies require that FAQ schema content must exactly match visible content on the page. The current FAQ page contains only 2 questions, while any hardcoded FAQ schema would introduce fabricated content. Schemas that don't match visible content are treated as spammy and can result in:
- Rich result suppression in search results
- Manual actions in Google Search Console
- Loss of trust for other structured data on the domain

**Future Implementation:** FAQ schema can be implemented once:
1. FAQ page content is expanded to include the questions/answers to be marked up, OR
2. A parser is built to extract FAQ content from `docs/faq.md` and generate schema dynamically

**Reference:** [Google FAQ Schema Guidelines](https://developers.google.com/search/docs/advanced/structured-data/faqpage)

---

### 3.3 Sitemap Management

**Current State:** Docusaurus includes `@docusaurus/plugin-sitemap` by default, which auto-generates `sitemap.xml`

**Verification Tasks:**
1. Confirm plugin is active in `docusaurus.config.js`
2. Verify build output includes `build/sitemap.xml`
3. Check that all versions (2.x, 1.0.18) are included
4. Register sitemap in Google Search Console

**Configuration (if needed):**
```javascript
// In docusaurus.config.js plugins array
{
  "@docusaurus/plugin-sitemap": {
    changefreq: "weekly",
    priority: 0.7,
    ignorePatterns: ["/tags/**"],
    filename: "sitemap.xml",
  }
}
```

**Expected Output:**
- `/sitemap.xml` — main sitemap
- `/sitemap-0.xml`, `/sitemap-1.xml`, etc. — chunked sitemaps for large sites
- All pages prioritized: docs=0.8, blog=0.7, others=0.5

---

### 3.4 Canonical URL Management

**Current State:** Docusaurus auto-generates canonical URLs for every page

**Verification Tasks:**
1. Inspect page source — confirm `<link rel="canonical" href="...">` present
2. Ensure canonical points to current version, not versioned paths
3. Handle blog post duplicates (avoid indexing multiple versions)

**Configuration (if needed):**
```javascript
// In docusaurus.config.js
{
  url: "https://zio.dev",
  baseUrl: "/",
  trailingSlash: false, // Strips trailing slashes to prevent duplicate content
}
```

**Expected Behavior:**
- `/overview/getting-started` → canonical: `https://zio.dev/overview/getting-started`
- `/1.0.18/overview/getting-started` → canonical: `https://zio.dev/overview/getting-started`
- `/blog/post-slug` → canonical: `https://zio.dev/blog/post-slug`

Note: Since `routeBasePath: '/'` is configured in docs preset, documentation is served from the root, not under `/docs/`.

---

## 4. Configuration Changes Required

| Component | File | Change |
|-----------|------|--------|
| robots.txt | `/website/static/robots.txt` | Create new file with crawler directives |
| Structured Data (Organization) | Theme component | Create Organization schema component |
| Structured Data (Website) | Theme component | Not implemented (deprecated by Google) |
| Structured Data (FAQ) | - | Deferred to future work (Google policy) |
| Sitemap | `docusaurus.config.js` | Verify plugin is active in preset-classic |
| Canonical URLs | `docusaurus.config.js` | Set `trailingSlash: false` for consistency |

---

## 5. Implementation Sequence

1. **Create `robots.txt`** — 15 minutes
   - Write static file in `/website/static/`
   - Add disallow rules, sitemap reference

2. **Implement Organization Schema** — 45 minutes
   - Create Docusaurus theme component for Organization JSON-LD
   - Inject into document head using Docusaurus Head component
   - Test with Google's Structured Data Testing Tool

3. **FAQ Schema Deferred** — 0 minutes (for future work)
   - Deferred until FAQ page content is expanded or parser is built
   - Google policy requires FAQ schema content match visible page content

4. **Verify Sitemap & Canonical URLs** — 15 minutes
   - Check `docusaurus.config.js` for existing configuration
   - Build project locally, inspect output
   - No changes needed if already configured

5. **Test & Validation** — 30 minutes
   - Run local build
   - Test with Google Search Console validation tools
   - Verify no errors in console

**Total Effort:** 2-3 hours

---

## 6. Testing & Validation

**Local Testing:**
```bash
cd website
npm run build
# Inspect build/robots.txt
# Inspect build/sitemap.xml
# Inspect build/index.html for <script type="application/ld+json">
```

**Google Tools:**
1. [Structured Data Testing Tool](https://search.google.com/test/rich-results) — validate JSON-LD
2. [Mobile-Friendly Test](https://search.google.com/test/mobile-friendly) — ensure responsive
3. [Google Search Console](https://search.google.com/search-console) — register sitemap, monitor crawl errors

**Search Engine Submission:**
- Submit sitemap to Google Search Console
- Submit to Bing Webmaster Tools
- Monitor indexing progress (24-48 hours)

---

## 7. Success Criteria

- ✅ `robots.txt` exists and is valid
- ✅ JSON-LD Organization schema present in page source on all pages
- ✅ Organization schema validates without errors
- ⏳ Website schema (Sitelinks Search Box) — deprecated by Google, not implemented
- ⏳ FAQ schema — deferred to future work (see section 3.2.3)
- ✅ Sitemap.xml exists and contains all doc versions
- ✅ Canonical URLs auto-generated for all pages
- ✅ No crawl errors in Google Search Console
- ✅ All documentation pages indexed within 48 hours

---

## 8. Maintenance & Future Work

**Post-Implementation:**
- Monitor Google Search Console monthly for crawl errors
- Update robots.txt if new exclude patterns needed
- Update Organization schema if contact info changes
- Add new structured data schemas if blog content grows (Article schema)

**Not Included in This Scope:**
- Content front-matter enhancement (descriptions, keywords)
- Image alt text additions
- SEO keyword strategy
- Internal linking optimization
- Page speed optimization

These are candidates for future Approach 1 or 3 work if desired.

---

## 9. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Overly restrictive robots.txt | Blocks important content | Test rules before deploy; whitelist critical paths |
| Malformed JSON-LD | Errors in Search Console | Validate with Google tool before commit |
| Duplicate content across versions | Indexing confusion | Verify canonical URL configuration |
| Sitemap too large | Crawl inefficiency | Docusaurus auto-chunks; monitor growth |

---

## 10. Deliverables

1. `robots.txt` file in `/website/static/` with crawler directives and rate limiting
2. Organization schema component (`OrganizationSchema.tsx`) injected globally on all pages
3. Canonical URL configuration (`trailingSlash: false`) for consistent URLs across versions
4. Verified sitemap generation and robot directives working correctly
5. Deployment ready — no content changes, only configuration

---

## Sign-Off

Design ready for implementation once approved by user.

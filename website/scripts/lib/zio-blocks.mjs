// Reads the "All Blocks" tables of zio-blocks' docs/index.md (the source of zioblocks.com/reference) into the list of
// blocks that zio.dev/ecosystem shows next to the libraries. Each `###` heading of those tables must be the label of a
// category of ecosystem-categories.json: both sites share one list of categories, so a heading zio.dev does not know
// is an error, not a new category.

const SITE = 'https://zioblocks.com/';

/** The page of a block on zioblocks.com, from its link in docs/index.md (`./reference/jwt.md` -> `/reference/jwt/`). */
export function blockUrl(link) {
  const path = link.replace(/^\.\//, '').replace(/\.mdx?$/, '').replace(/(^|\/)index$/, '');
  return SITE + path + (path.endsWith('/') || path === '' ? '' : '/');
}

/**
 * The anchor of a category on zioblocks.com/reference/: `groupId` of zio-blocks' website/src/lib/reference-directory.mjs.
 * Both sites use the same labels, so zio.dev can link to the matching group.
 */
export function referenceAnchor(label) {
  return 'ref-' + label.toLowerCase().replace(/&/g, ' ').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

/** The blocks of docs/index.md, each with the id of its category, in the order of the page. */
export function parseBlocks(markdown, categories) {
  const start = markdown.indexOf('\n## All Blocks');
  if (start < 0) throw new Error('docs/index.md has no "## All Blocks" section');
  const rest = markdown.slice(start + 1);
  const end = rest.search(/\n(## |---)/);
  const section = end < 0 ? rest : rest.slice(0, end);

  const byLabel = new Map(categories.map((c) => [c.label, c.id]));
  const blocks = [];
  let category = null;
  for (const line of section.split('\n')) {
    const heading = line.match(/^### (.+?)\s*$/);
    if (heading) {
      category = byLabel.get(heading[1]);
      if (!category) {
        throw new Error(
          `docs/index.md heading "${heading[1]}" is not a category of ecosystem-categories.json ` +
            `(${categories.map((c) => c.label).join(', ')})`
        );
      }
      continue;
    }
    const row = line.match(/^\| \[([^\]]+)\]\(([^)]+)\) \| `([^`]+)` \| ([^|]+) \| ([^|]+) \| (.+?) \|\s*$/);
    if (!row) continue;
    if (!category) throw new Error(`docs/index.md row "${row[1]}" comes before any ### heading`);
    const [, name, link, artifact, platforms, scala, description] = row;
    blocks.push({
      name,
      href: blockUrl(link),
      artifact,
      platforms: platforms.split('·').map((p) => p.trim()),
      scala: scala.trim(),
      description: description.trim(),
      category,
    });
  }
  if (blocks.length === 0) throw new Error('docs/index.md "All Blocks" has no rows');
  return blocks;
}

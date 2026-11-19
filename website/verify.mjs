import { access, readFile, stat } from "node:fs/promises";
import { resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const origin = "https://quantoot.qqs.tw";
const output = fileURLToPath(new URL("./dist", import.meta.url));
const pages = new Map([
  ["/", "The social web"],
  ["/privacy/", "Privacy policy"],
  ["/support/", "Support &amp; safety"],
]);

for (const [route, heading] of pages) {
  const html = await readFile(resolve(output, `.${route}index.html`), "utf8");
  const canonical = `<link rel="canonical" href="${origin}${route}">`;
  if (!html.includes(canonical) || !html.includes(heading)) {
    throw new Error(`Missing heading or canonical URL on ${route}`);
  }
  if (/\sstyle="/i.test(html) || /<style\b/i.test(html) || /\son[a-z]+="/i.test(html)) {
    throw new Error(`CSP violation: inline style or event handler found on ${route}`);
  }
  for (const [, ref] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
    if (ref.startsWith("mailto:") || ref.startsWith("#")) continue;
    const target = new URL(ref, origin + route);
    if (target.origin !== origin) continue;
    const filename = resolve(output, `.${decodeURIComponent(target.pathname)}`);
    if (filename !== output && !filename.startsWith(output + sep)) {
      throw new Error(`Unexpected path ${ref}`);
    }
    const info = await stat(filename);
    if (info.isDirectory()) await access(resolve(filename, "index.html"));
  }
}

const css = await readFile(resolve(output, "assets/site.css"), "utf8");
const js = await readFile(resolve(output, "assets/site.js"), "utf8");
if (!css.trim() || !js.trim()) {
  throw new Error("Compiled CSS or JS bundle is empty");
}
if (/url\(\s*["']?data:/i.test(css)) {
  throw new Error("CSP violation: data: URI found in CSS under img-src 'self'");
}

await access(resolve(output, "_headers"));
console.log("Homepage, privacy policy, support, CSP checks, and local links passed.");

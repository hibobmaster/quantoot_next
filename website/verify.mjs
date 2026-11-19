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
  for (const [, ref] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
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
await access(resolve(output, "_headers"));
console.log("Homepage, privacy policy, support and their local links passed.");

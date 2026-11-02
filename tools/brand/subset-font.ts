// Subsets the bundled Google Sans Flex variable font for the app.
//
//   cd tools/brand && bun install && bun subset-font.ts
//
// Source:  tools/brand/fonts/GoogleSansFlex-full.ttf  (full font, SIL OFL 1.1, committed)
// Output:  app/src/main/res/font/google_sans_flex.ttf
//
// The app only drives two axes (ui/theme/Type.kt): wght 400..700 and ROND 0..100.
// Every other axis is pinned to the font's default instance, which is exactly what
// Android renders today (FontVariation.Settings leaves unset axes at their default,
// and Compose never applies opsz on its own), so the rendered text does not change.
//
// Glyph coverage: every codepoint the source font maps is kept. The font is Latin
// only (686 glyphs), so glyph pruning would save almost nothing, and keeping the
// whole cmap guarantees user-entered text (mask descriptions, names) never falls
// back where it did not before. The script then proves the claim: every character
// in res/values*/strings*.xml that the source font covers is still covered.
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import subsetFont from "subset-font";
import * as fontkit from "fontkit";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "../..");
const source = join(here, "fonts/GoogleSansFlex-full.ttf");
const target = join(repo, "app/src/main/res/font/google_sans_flex.ttf");
const resDir = join(repo, "app/src/main/res");

const full = fontkit.openSync(source) as fontkit.Font;
const axes = full.variationAxes as Record<string, { min: number; default: number; max: number }>;

const variationAxes = {
  wght: { min: 400, max: 700 },
  ROND: { min: 0, max: 100 },
  opsz: axes.opsz.default,
  wdth: axes.wdth.default,
  slnt: axes.slnt.default,
};

const text = String.fromCodePoint(...(full.characterSet as number[]));
const out = await subsetFont(readFileSync(source), text, {
  targetFormat: "sfnt",
  variationAxes,
  // copyright, family, subfamily, unique id, full name, version, PostScript name, license text/url
  preserveNameIds: [0, 1, 2, 3, 4, 5, 6, 13, 14],
});
writeFileSync(target, out);

// ---- verification -------------------------------------------------------
const sub = fontkit.openSync(target) as fontkit.Font;
const subAxes = sub.variationAxes as typeof axes;
const fail = (m: string): never => {
  console.error("FAIL: " + m);
  process.exit(1);
};

const expect: Record<string, [number, number]> = { wght: [400, 700], ROND: [0, 100] };
for (const [tag, [min, max]] of Object.entries(expect)) {
  const a = subAxes[tag];
  if (!a || a.min !== min || a.max !== max) fail(`axis ${tag} is ${JSON.stringify(a)}, wanted ${min}..${max}`);
}
for (const tag of ["opsz", "wdth", "slnt"]) {
  if (subAxes[tag]) fail(`axis ${tag} should have been pinned out`);
}

const missing = (full.characterSet as number[]).filter((c) => !sub.hasGlyphForCodePoint(c));
if (missing.length) fail(`${missing.length} codepoints lost: ${missing.slice(0, 10).join(",")}`);

// strings coverage report: which shipped characters the font covers, which fall back
const fallbacks = new Map<string, Set<number>>();
let covered = 0;
for (const d of readdirSync(resDir).filter((d) => d.startsWith("values"))) {
  for (const f of readdirSync(join(resDir, d)).filter((f) => /^strings.*\.xml$/.test(f))) {
    for (const ch of readFileSync(join(resDir, d, f), "utf8")) {
      const c = ch.codePointAt(0)!;
      if (c < 32 || sub.hasGlyphForCodePoint(c)) {
        covered++;
        continue;
      }
      if (full.hasGlyphForCodePoint(c)) fail(`U+${c.toString(16)} in ${d}/${f} lost in the subset`);
      (fallbacks.get(d) ?? fallbacks.set(d, new Set()).get(d)!).add(c);
    }
  }
}

console.log(`source ${statSync(source).size} bytes -> subset ${statSync(target).size} bytes`);
console.log(`axes: ${Object.entries(subAxes).map(([t, a]) => `${t} ${a.min}..${a.max}`).join(", ")}`);
console.log(`codepoints kept: ${(full.characterSet as number[]).length}, glyphs ${full.numGlyphs} -> ${sub.numGlyphs}`);
console.log("locales whose characters fall back to the system font (not in Google Sans Flex):");
for (const [d, set] of [...fallbacks].sort()) console.log(`  ${d}: ${set.size} distinct chars`);

/**
 * Read (and, with `push`, replace) FastMask's Google Play store listing over the
 * Play Developer API.
 *
 *   bun tools/play/PlayListing.ts <key.json> <pkg> read <outDir>
 *   bun tools/play/PlayListing.ts <key.json> <pkg> push <assetsDir> [--commit]
 *
 * `read` writes listing.json (titles, descriptions per language) and downloads every
 * listing image into <outDir>/<lang>/<imageType>/NN.png, then deletes the edit.
 *
 * `push` expects <assetsDir>/<lang>/{icon,featureGraphic,phoneScreenshots,...}/*.png
 * and optional <assetsDir>/<lang>/listing.json ({ title, shortDescription,
 * fullDescription }). For every image type present it deletes the existing set and
 * uploads the new files in name order. Without --commit the edit is validated and
 * then deleted (dry run); with --commit it is committed and goes live.
 *
 * The service-account key is read from the given path and never printed.
 */
import { createSign } from "node:crypto";
import { mkdirSync, readFileSync, readdirSync, existsSync, writeFileSync, statSync } from "node:fs";
import { join } from "node:path";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3";
const IMAGE_TYPES = [
  "icon",
  "featureGraphic",
  "phoneScreenshots",
  "sevenInchScreenshots",
  "tenInchScreenshots",
] as const;

const b64u = (b: Buffer | string) =>
  Buffer.from(b).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function token(keyPath: string): Promise<string> {
  const key = JSON.parse(readFileSync(keyPath, "utf8")) as { client_email: string; private_key: string };
  const iat = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64u(JSON.stringify({ iss: key.client_email, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 3600 }));
  const sig = createSign("RSA-SHA256").update(`${head}.${claims}`).sign(key.private_key);
  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${head}.${claims}.${b64u(sig)}` }),
  });
  if (!res.ok) throw new Error(`token exchange failed: ${res.status}`);
  return ((await res.json()) as { access_token: string }).access_token;
}

async function call(t: string, method: string, url: string, body?: BodyInit, type = "application/json") {
  const res = await fetch(url, { method, headers: { Authorization: `Bearer ${t}`, ...(body ? { "Content-Type": type } : {}) }, body });
  if (!res.ok) throw new Error(`${method} ${url.replace(/\?.*/, "")} failed: ${res.status} ${await res.text()}`);
  const text = await res.text();
  return text ? JSON.parse(text) : {};
}

async function main() {
  const [keyPath, pkg, mode, dir, ...flags] = process.argv.slice(2);
  if (!keyPath || !pkg || !mode || !dir) {
    console.error("usage: PlayListing.ts <key.json> <pkg> read <outDir> | push <assetsDir> [--commit]");
    process.exit(2);
  }
  const t = await token(keyPath);
  const app = `${API}/applications/${pkg}`;
  const edit = await call(t, "POST", `${app}/edits`);
  const e = `${app}/edits/${edit.id}`;
  let committed = false;
  try {
    if (mode === "read") {
      const { listings = [] } = await call(t, "GET", `${e}/listings`);
      mkdirSync(dir, { recursive: true });
      writeFileSync(join(dir, "listing.json"), JSON.stringify(listings, null, 2));
      for (const l of listings) {
        console.log(`${l.language}: "${l.title}" | short ${l.shortDescription?.length ?? 0} ch | full ${l.fullDescription?.length ?? 0} ch`);
        for (const type of IMAGE_TYPES) {
          const { images = [] } = await call(t, "GET", `${e}/listings/${l.language}/${type}`);
          if (!images.length) continue;
          const out = join(dir, l.language, type);
          mkdirSync(out, { recursive: true });
          let i = 0;
          for (const img of images) {
            const bin = await fetch(`${img.url}=s0`);
            writeFileSync(join(out, `${String(++i).padStart(2, "0")}.png`), Buffer.from(await bin.arrayBuffer()));
          }
          console.log(`  ${type}: ${images.length}`);
        }
      }
    } else if (mode === "push") {
      for (const lang of readdirSync(dir).filter((d) => statSync(join(dir, d)).isDirectory())) {
        const listingFile = join(dir, lang, "listing.json");
        if (existsSync(listingFile)) {
          const l = JSON.parse(readFileSync(listingFile, "utf8"));
          await call(t, "PUT", `${e}/listings/${lang}`, JSON.stringify({ language: lang, ...l }));
          console.log(`${lang}: listing text updated`);
        }
        for (const type of IMAGE_TYPES) {
          const tdir = join(dir, lang, type);
          if (!existsSync(tdir)) continue;
          const files = readdirSync(tdir).filter((f) => /\.(png|jpe?g)$/i.test(f)).sort();
          if (!files.length) continue;
          await call(t, "DELETE", `${e}/listings/${lang}/${type}`);
          for (const f of files) {
            const mime = /\.png$/i.test(f) ? "image/png" : "image/jpeg";
            await call(t, "POST", `${UPLOAD}/applications/${pkg}/edits/${edit.id}/listings/${lang}/${type}?uploadType=media`, readFileSync(join(tdir, f)), mime);
          }
          console.log(`${lang}: ${type} replaced with ${files.length}`);
        }
      }
      await call(t, "POST", `${e}:validate`);
      console.log("edit validated");
      if (flags.includes("--commit")) {
        await call(t, "POST", `${e}:commit`);
        committed = true;
        console.log(`committed edit ${edit.id}`);
      } else {
        console.log("dry run: not committed (pass --commit to publish)");
      }
    } else {
      throw new Error(`unknown mode ${mode}`);
    }
  } finally {
    if (!committed) await call(t, "DELETE", e).catch(() => {});
  }
}

await main();

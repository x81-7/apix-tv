// Edge Function: upload-app-assets
// Accepts a base64-encoded PNG (1024x1024 ideally) from the panel and pushes it
// straight into the GitHub repo at every Android launcher density + the iOS
// AppIcon-1024.png. The next CI build picks it up automatically.
//
// Body:
//   { kind: "icon" | "splash", pngBase64: "iVBORw0..." }
//
// Densities written for icon:
//   mipmap-mdpi/ic_launcher.png            48
//   mipmap-hdpi/ic_launcher.png            72
//   mipmap-xhdpi/ic_launcher.png           96
//   mipmap-xxhdpi/ic_launcher.png         144
//   mipmap-xxxhdpi/ic_launcher.png        192
//   + ic_launcher_round.png at each density
// iOS:
//   ios/APiXTV/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
//
// Splash writes:
//   android/app/src/main/res/drawable/splash_image.png
//   ios/APiXTV/Assets.xcassets/Splash.imageset/splash.png (+ Contents.json)

import { uploadBinaryToGithub } from "../_shared/github.ts";
import { getGithubCreds } from "../_shared/github-creds.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const ICON_DENSITIES: Array<{ dir: string; size: number }> = [
  { dir: 'mipmap-mdpi', size: 48 },
  { dir: 'mipmap-hdpi', size: 72 },
  { dir: 'mipmap-xhdpi', size: 96 },
  { dir: 'mipmap-xxhdpi', size: 144 },
  { dir: 'mipmap-xxxhdpi', size: 192 },
];

function b64ToBytes(b64: string): Uint8Array {
  const clean = b64.replace(/^data:image\/\\w+;base64,/, '');
  const bin = atob(clean);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/**
 * Resize a PNG using the canvas-less ImageScript Deno port. We don't want a
 * heavy native dep — ImageScript is a tiny WASM-free TypeScript package.
 */
async function resizePng(srcBytes: Uint8Array, size: number): Promise<Uint8Array> {
  const { Image } = await import('https://deno.land/x/imagescript@1.2.17/mod.ts');
  const img = await Image.decode(srcBytes);
  // Cover-fit (preserve aspect, pad transparent) — simplest is just resize.
  const resized = img.resize(size, size);
  return await resized.encode();
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const body = await req.json();
    const kind = body?.kind;
    const pngBase64: string | undefined = body?.pngBase64;
    if (!pngBase64 || typeof pngBase64 !== 'string') throw new Error('pngBase64 required');
    if (kind !== 'icon' && kind !== 'splash') throw new Error('kind must be "icon" or "splash"');

    const { token, repo } = await getGithubCreds();
    const src = b64ToBytes(pngBase64);

    const written: string[] = [];

    if (kind === 'icon') {
      // Android densities
      for (const d of ICON_DENSITIES) {
        const resized = await resizePng(src, d.size);
        for (const name of ['ic_launcher.png', 'ic_launcher_round.png']) {
          const path = `android/app/src/main/res/${d.dir}/${name}`;
          await uploadBinaryToGithub(token, repo, path, resized, `chore(icon): update ${d.dir}/${name}`);
          written.push(path);
        }
      }
      // iOS 1024 master
      const ios1024 = await resizePng(src, 1024);
      const iosPath = 'ios/APiXTV/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png';
      await uploadBinaryToGithub(token, repo, iosPath, ios1024, 'chore(icon): update iOS AppIcon');
      written.push(iosPath);
    } else {
      // Splash — keep original size, push to both platforms.
      const androidPath = 'android/app/src/main/res/drawable/splash_image.png';
      await uploadBinaryToGithub(token, repo, androidPath, src, 'chore(splash): update Android splash');
      written.push(androidPath);

      const iosPath = 'ios/APiXTV/Assets.xcassets/Splash.imageset/splash.png';
      await uploadBinaryToGithub(token, repo, iosPath, src, 'chore(splash): update iOS splash');
      written.push(iosPath);
    }

    return new Response(JSON.stringify({ ok: true, written }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e: any) {
    console.error('upload-app-assets error', e);
    return new Response(JSON.stringify({ error: e.message ?? 'Unknown error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});

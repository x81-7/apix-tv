// AES-256-GCM helpers + tree JSON builder for encrypted_data.json

const enc = new TextEncoder();

function toArrayBuffer(view: Uint8Array): ArrayBuffer {
  const { buffer, byteOffset, byteLength } = view;
  if (buffer instanceof ArrayBuffer && byteOffset === 0 && byteLength === buffer.byteLength) {
    return buffer;
  }
  return view.slice().buffer;
}

export function b64encode(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.byteLength; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

export function b64decode(s: string): Uint8Array {
  const bin = atob(s);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

async function importAesKey(rawKey: Uint8Array) {
  return await crypto.subtle.importKey(
    'raw',
    toArrayBuffer(rawKey),
    { name: 'AES-GCM' },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function aesEncrypt(rawKey: Uint8Array, plaintext: string) {
  const key = await importAesKey(rawKey);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    toArrayBuffer(enc.encode(plaintext))
  );
  return { iv: b64encode(iv), data: b64encode(ct) };
}

export async function aesDecrypt(
  rawKey: Uint8Array,
  ivB64: string,
  dataB64: string
): Promise<string> {
  const key = await importAesKey(rawKey);
  const pt = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: toArrayBuffer(b64decode(ivB64)) },
    key,
    toArrayBuffer(b64decode(dataB64))
  );
  return new TextDecoder().decode(pt);
}

export async function deriveMasterKey(masterSecret: string): Promise<Uint8Array> {
  const hash = await crypto.subtle.digest('SHA-256', toArrayBuffer(enc.encode(masterSecret)));
  return new Uint8Array(hash);
}

/** Combine two key parts into a 32-byte AES key via SHA-256(part1 || part2 || salt). */
export async function combineKeys(
  internalB64: string,
  externalB64: string,
  salt: string
): Promise<Uint8Array> {
  const a = b64decode(internalB64);
  const b = b64decode(externalB64);
  const s = enc.encode(salt);
  const merged = new Uint8Array(a.length + b.length + s.length);
  merged.set(a, 0);
  merged.set(b, a.length);
  merged.set(s, a.length + b.length);
  const h = await crypto.subtle.digest('SHA-256', toArrayBuffer(merged));
  return new Uint8Array(h);
}

// ===== App data tree builder =====
// Produces the canonical JSON tree that the Android app expects.

type Row = Record<string, unknown>;

export function buildAppDataTree(input: {
  categories: Row[];
  channels: Row[];
  sideMenus: Row[];
  subChannels: Row[];
  systemSettings: Row[];
}) {
  const id = (r: Row) => (r.legacy_id as string) || (r.id as string);

  // Categories with nested channels
  const categories: Record<string, Row> = {};
  for (const c of input.categories) {
    const cid = id(c);
    categories[cid] = {
      id: cid,
      name: c.name,
      sortOrder: c.sort_order ?? 0,
      hidden: c.hidden ?? false,
      channels: {},
    };
  }
  for (const ch of input.channels) {
    const catKey = ch.category_id as string | null;
    if (!catKey) continue;
    // Find category whose db id matches
    const targetCatId = Object.keys(categories).find((k) => {
      const matchesLegacy = input.categories.find(
        (c) => id(c) === k && c.id === catKey
      );
      return !!matchesLegacy;
    });
    if (!targetCatId) continue;
    const chId = id(ch);
    (categories[targetCatId].channels as Record<string, Row>)[chId] = mapChannel(
      ch,
      input.sideMenus,
      chId
    );
  }

  // Side menus with nested sub-channels
  const sideMenus: Record<string, Row> = {};
  for (const m of input.sideMenus) {
    const mid = id(m);
    sideMenus[mid] = {
      id: mid,
      name: m.name,
      sortOrder: m.sort_order ?? 0,
      channels: {},
    };
  }
  for (const sc of input.subChannels) {
    const menuKey = sc.side_menu_id as string;
    const targetMenuId = Object.keys(sideMenus).find((k) => {
      const matches = input.sideMenus.find(
        (m) => id(m) === k && m.id === menuKey
      );
      return !!matches;
    });
    if (!targetMenuId) continue;
    const sid = id(sc);
    (sideMenus[targetMenuId].channels as Record<string, Row>)[sid] = mapSubChannel(sc, sid);
  }

  // System settings flattened by key
  const settings: Record<string, unknown> = {};
  for (const s of input.systemSettings) {
    settings[s.key as string] = s.value;
  }

  return {
    categories,
    sideMenus,
    settings,
    generatedAt: new Date().toISOString(),
  };
}

function mapChannel(ch: Row, sideMenus: Row[], chId: string): Row {
  const id = (r: Row) => (r.legacy_id as string) || (r.id as string);
  let sideMenuLegacyId: string | undefined;
  if (ch.side_menu_id) {
    const m = sideMenus.find((sm) => sm.id === ch.side_menu_id);
    if (m) sideMenuLegacyId = id(m);
  }
  return {
    id: chId,
    name: ch.name,
    imageUrl: ch.image_url ?? '',
    sortOrder: ch.sort_order ?? 0,
    hidden: ch.hidden ?? false,
    actionType: ch.action_type ?? 'direct_play',
    sideMenuId: sideMenuLegacyId ?? null,
    externalUrl: ch.external_url ?? null,
    preferredPlayer: ch.preferred_player ?? null,
    stream: ch.web_stream ?? null,
    androidStream: ch.android_stream ?? null,
    androidActionType: ch.android_action_type ?? null,
  };
}

function mapSubChannel(sc: Row, sid: string): Row {
  return {
    id: sid,
    name: sc.name,
    imageUrl: sc.image_url ?? '',
    sortOrder: sc.sort_order ?? 0,
    hidden: sc.hidden ?? false,
    preferredPlayer: sc.preferred_player ?? null,
    stream: sc.web_stream ?? null,
    androidStream: sc.android_stream ?? null,
    androidActionType: sc.android_action_type ?? null,
  };
}

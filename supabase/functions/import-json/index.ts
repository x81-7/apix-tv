import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

/**
 * Replace-mode import.
 * Accepts:
 *  - BackupButton export shape (v2) with nested `categories[].channels[]`
 *    and `sideMenus[].sub_channels[]`, plus optional `raw` flat arrays.
 *  - Legacy flat shape: { categories, sideMenus, subChannels, channels, ... }
 *  - Raw wrappers or unwrapped objects.
 *  - `systemSettings` as array (rows) or object (key/value map).
 */

type AnyObj = Record<string, any>;

const toArray = (v: any): AnyObj[] => {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  if (typeof v === 'object') {
    return Object.entries(v).map(([k, val]) => ({ legacy_id: k, ...(val as AnyObj) }));
  }
  return [];
};

const num = (v: any, d = 0) => (typeof v === 'number' ? v : Number(v ?? d) || d);
const bool = (v: any) => v === true || v === 'true';
const str = (v: any): string | null => (v == null ? null : String(v));

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    const body = await req.json();
    const root = body?.data ?? body;
    if (!root || typeof root !== 'object') throw new Error('Invalid JSON payload');

    // Prefer flat `raw` arrays if present (from v2 export)
    const raw = root.raw ?? {};

    const catsSrc = raw.categories ?? root.categories;
    const menusSrc = raw.side_menus ?? raw.sideMenus ?? root.sideMenus ?? root.side_menus;

    const cats = toArray(catsSrc);
    const menus = toArray(menusSrc);

    // Channels: flat if available, else flatten from nested categories[].channels + orphanChannels
    let chans: AnyObj[] = [];
    if (raw.channels) {
      chans = toArray(raw.channels);
    } else if (Array.isArray(root.channels)) {
      chans = root.channels;
    } else {
      // Flatten nested
      for (const c of cats) {
        const nested = (c as AnyObj).channels;
        if (Array.isArray(nested)) {
          for (const ch of nested) chans.push({ ...ch, category_id: ch.category_id ?? c.id });
        }
      }
      if (Array.isArray(root.orphanChannels)) chans.push(...root.orphanChannels);
    }

    // Sub-channels: flat if available, else flatten from nested sideMenus[].sub_channels
    let subs: AnyObj[] = [];
    if (raw.sub_channels ?? raw.subChannels) {
      subs = toArray(raw.sub_channels ?? raw.subChannels);
    } else if (root.subChannels || root.sub_channels) {
      subs = toArray(root.subChannels ?? root.sub_channels);
    } else {
      for (const m of menus) {
        const nested = (m as AnyObj).sub_channels ?? (m as AnyObj).subChannels;
        if (Array.isArray(nested)) {
          for (const sc of nested) subs.push({ ...sc, side_menu_id: sc.side_menu_id ?? m.id });
        }
      }
    }

    // 1) Wipe existing data (children first to avoid FK issues)
    await supabase.from('channels').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    await supabase.from('sub_channels').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    await supabase.from('side_menus').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    await supabase.from('categories').delete().neq('id', '00000000-0000-0000-0000-000000000000');

    // 2) Categories — keep original UUIDs when provided so nested refs still match
    const catIdMap = new Map<string, string>(); // old-id/legacy -> new uuid
    if (cats.length) {
      const rows = cats.map((c, i) => {
        const row: AnyObj = {
          legacy_id: str(c.legacy_id ?? c.id ?? c.key) ?? `cat_${i}`,
          name: str(c.name) ?? 'بدون اسم',
          sort_order: num(c.sort_order ?? c.order, i),
          hidden: bool(c.hidden),
        };
        if (c.id && typeof c.id === 'string' && c.id.length === 36) row.id = c.id;
        return row;
      });
      const { data, error } = await supabase
        .from('categories')
        .insert(rows)
        .select('id, legacy_id');
      if (error) throw new Error('categories: ' + error.message);
      cats.forEach((c, i) => {
        const newRow = data?.[i];
        if (!newRow) return;
        if (c.id) catIdMap.set(String(c.id), newRow.id);
        if (c.legacy_id) catIdMap.set(String(c.legacy_id), newRow.id);
      });
    }

    // 3) Side menus
    const menuIdMap = new Map<string, string>();
    if (menus.length) {
      const rows = menus.map((m, i) => {
        const row: AnyObj = {
          legacy_id: str(m.legacy_id ?? m.id ?? m.key) ?? `menu_${i}`,
          name: str(m.name) ?? 'بدون اسم',
          sort_order: num(m.sort_order ?? m.order, i),
        };
        if (m.id && typeof m.id === 'string' && m.id.length === 36) row.id = m.id;
        return row;
      });
      const { data, error } = await supabase
        .from('side_menus')
        .insert(rows)
        .select('id, legacy_id');
      if (error) throw new Error('side_menus: ' + error.message);
      menus.forEach((m, i) => {
        const newRow = data?.[i];
        if (!newRow) return;
        if (m.id) menuIdMap.set(String(m.id), newRow.id);
        if (m.legacy_id) menuIdMap.set(String(m.legacy_id), newRow.id);
      });
    }

    const resolveMenu = (v: any): string | null => {
      if (!v) return null;
      const s = String(v);
      return menuIdMap.get(s) ?? null;
    };
    const resolveCat = (v: any): string | null => {
      if (!v) return null;
      const s = String(v);
      return catIdMap.get(s) ?? null;
    };

    // 4) Sub-channels
    let subsInserted = 0;
    if (subs.length) {
      const rows = subs
        .map((s, i) => {
          const sideMenuUuid =
            resolveMenu(s.side_menu_id ?? s.sideMenuId ?? s.menu_id) ??
            [...menuIdMap.values()][0];
          if (!sideMenuUuid) return null;
          return {
            legacy_id: str(s.legacy_id ?? s.id ?? s.key) ?? `sub_${i}`,
            side_menu_id: sideMenuUuid,
            name: str(s.name) ?? 'بدون اسم',
            image_url: str(s.image_url ?? s.image),
            sort_order: num(s.sort_order ?? s.order, i),
            hidden: bool(s.hidden),
            preferred_player: str(s.preferred_player),
            web_stream: s.web_stream ?? null,
            android_stream: s.android_stream ?? null,
            android_action_type: str(s.android_action_type),
          };
        })
        .filter((r): r is NonNullable<typeof r> => r !== null);
      if (rows.length) {
        // Insert in chunks to avoid payload limits
        const chunk = 200;
        for (let i = 0; i < rows.length; i += chunk) {
          const slice = rows.slice(i, i + chunk);
          const { error } = await supabase.from('sub_channels').insert(slice);
          if (error) throw new Error('sub_channels: ' + error.message);
          subsInserted += slice.length;
        }
      }
    }

    // 5) Channels
    let chansInserted = 0;
    if (chans.length) {
      const rows = chans.map((c, i) => ({
        legacy_id: str(c.legacy_id ?? c.id ?? c.key) ?? `ch_${i}`,
        category_id: resolveCat(c.category_id ?? c.categoryId),
        side_menu_id: resolveMenu(c.side_menu_id ?? c.sideMenuId),
        name: str(c.name) ?? 'بدون اسم',
        image_url: str(c.image_url ?? c.image),
        sort_order: num(c.sort_order ?? c.order, i),
        hidden: bool(c.hidden),
        action_type: str(c.action_type) ?? 'direct_play',
        android_action_type: str(c.android_action_type),
        android_stream: c.android_stream ?? null,
        web_stream: c.web_stream ?? null,
        external_url: str(c.external_url),
        preferred_player: str(c.preferred_player),
      }));
      const chunk = 200;
      for (let i = 0; i < rows.length; i += chunk) {
        const slice = rows.slice(i, i + chunk);
        const { error } = await supabase.from('channels').insert(slice);
        if (error) throw new Error('channels: ' + error.message);
        chansInserted += slice.length;
      }
    }

    // 6) system_settings: array of {key,value} OR object map OR individual keys
    const ssUpserts: { key: string; value: any }[] = [];

    if (Array.isArray(root.systemSettings)) {
      for (const r of root.systemSettings) {
        if (r && typeof r === 'object' && r.key) {
          ssUpserts.push({ key: String(r.key), value: r.value ?? null });
        }
      }
    } else if (root.systemSettings && typeof root.systemSettings === 'object') {
      for (const [k, v] of Object.entries(root.systemSettings)) {
        ssUpserts.push({ key: k, value: v });
      }
    }

    // Also accept well-known top-level keys (legacy)
    const settingsKeys = [
      'appSettings',
      'androidConfig',
      'webConfig',
      'playerConfig',
      'securityConfig',
      'adConfig',
      'appUpdate',
      'notifications',
      'sideMenuItems',
    ];
    for (const k of settingsKeys) {
      if (root[k] !== undefined) ssUpserts.push({ key: k, value: root[k] });
    }

    for (const row of ssUpserts) {
      const { error } = await supabase
        .from('system_settings')
        .upsert(row, { onConflict: 'key' });
      if (error) console.warn('system_settings upsert failed for', row.key, error.message);
    }

    return new Response(
      JSON.stringify({
        success: true,
        counts: {
          categories: cats.length,
          sideMenus: menus.length,
          subChannels: subsInserted,
          channels: chansInserted,
          systemSettings: ssUpserts.length,
        },
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } },
    );
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('import-json error:', message);
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});

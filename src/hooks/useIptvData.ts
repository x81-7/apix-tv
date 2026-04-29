import { useEffect, useMemo, useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import type { Category, SideMenu, Channel, SubChannel, StreamConfig, AndroidStreamConfig } from '@/types/admin';

export interface IptvDataState {
  categories: Record<string, Category>;
  sideMenus: Record<string, SideMenu>;
  loading: boolean;
  error: string | null;
}

/** Map a Supabase channel row to the app's Channel type */
function mapChannel(row: any): Channel {
  const ws = row.web_stream as any;
  const as_ = row.android_stream as any;
  return {
    id: row.id,
    name: row.name,
    imageUrl: row.image_url ?? '',
    sortOrder: row.sort_order ?? 0,
    actionType: row.action_type ?? 'direct_play',
    hidden: row.hidden ?? false,
    sideMenuId: row.side_menu_id ?? undefined,
    externalUrl: row.external_url ?? undefined,
    preferredPlayer: row.preferred_player ?? undefined,
    iosPlayerType: row.ios_player_type ?? undefined,
    stream: ws ? mapWebStream(ws) : undefined,
    androidStream: as_ ?? undefined,
    androidActionType: row.android_action_type ?? undefined,
  };
}

function mapSubChannel(row: any): SubChannel {
  const ws = row.web_stream as any;
  return {
    id: row.id,
    name: row.name,
    imageUrl: row.image_url ?? '',
    sortOrder: row.sort_order ?? 0,
    hidden: row.hidden ?? false,
    preferredPlayer: row.preferred_player ?? undefined,
    stream: ws ? mapWebStream(ws) : { url: '' },
    androidStream: row.android_stream ?? undefined,
    androidActionType: row.android_action_type ?? undefined,
  };
}

function mapWebStream(ws: any): StreamConfig {
  return {
    url: ws.url ?? '',
    userAgent: ws.userAgent ?? ws.headers?.userAgent,
    referrer: ws.referrer ?? ws.headers?.referrer,
    cookies: ws.cookies ?? ws.headers?.cookie,
    origin: ws.origin ?? ws.headers?.origin,
    drm: ws.drm,
    customHeaders: ws.customHeaders,
    drmLicenseHeaders: ws.drmLicenseHeaders,
    backupUrl: ws.backupUrl,
    audioSources: ws.audioSources,
    subtitleUrl: ws.subtitleUrl,
    dynamicApi: ws.dynamicApi,
    forcedAspectRatio: ws.forcedAspectRatio,
    lockAspectRatio: ws.lockAspectRatio,
    logoOverlay: ws.logoOverlay,
  };
}

export const useIptvData = (): IptvDataState => {
  const [categories, setCategories] = useState<Record<string, Category>>({});
  const [sideMenus, setSideMenus] = useState<Record<string, SideMenu>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [catsRes, chansRes, menusRes, subsRes] = await Promise.all([
          supabase.from('categories').select('*').order('sort_order'),
          supabase.from('channels').select('*').order('sort_order'),
          supabase.from('side_menus').select('*').order('sort_order'),
          supabase.from('sub_channels').select('*').order('sort_order'),
        ]);
        if (cancelled) return;
        if (catsRes.error) throw catsRes.error;
        if (chansRes.error) throw chansRes.error;
        if (menusRes.error) throw menusRes.error;
        if (subsRes.error) throw subsRes.error;

        // Build categories with channels
        const catMap: Record<string, Category> = {};
        for (const cat of catsRes.data ?? []) {
          catMap[cat.id] = {
            id: cat.id,
            name: cat.name,
            sortOrder: cat.sort_order ?? 0,
            hidden: cat.hidden ?? false,
            channels: {},
          };
        }
        for (const ch of chansRes.data ?? []) {
          if (ch.category_id && catMap[ch.category_id]) {
            catMap[ch.category_id].channels[ch.id] = mapChannel(ch);
          }
        }
        setCategories(catMap);

        // Build side menus with sub-channels
        const menuMap: Record<string, SideMenu> = {};
        for (const m of menusRes.data ?? []) {
          menuMap[m.id] = { id: m.id, name: m.name, sortOrder: m.sort_order ?? 0, channels: {} };
        }
        for (const sc of subsRes.data ?? []) {
          if (sc.side_menu_id && menuMap[sc.side_menu_id]) {
            menuMap[sc.side_menu_id].channels[sc.id] = mapSubChannel(sc);
          }
        }
        setSideMenus(menuMap);
      } catch (err: any) {
        if (!cancelled) setError(err?.message || 'Failed to load data');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  return { categories, sideMenus, loading, error };
};

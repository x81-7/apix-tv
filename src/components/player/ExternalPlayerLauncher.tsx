import React, { useState, useEffect } from 'react';
import { X, ExternalLink, Download, Smartphone } from 'lucide-react';
import { IOS_PLAYERS, isIOS, launchExternalPlayer } from '@/lib/iosPlayers';
import type { iOSPlayerApp } from '@/types/admin';

interface ExternalPlayerLauncherProps {
  url: string;
  title: string;
  playerApp: iOSPlayerApp;
  onClose: () => void;
  onFallbackToWeb?: () => void;
}

/**
 * External Player Launcher - Opens stream in iOS external apps
 * Shows a UI with launch button + App Store fallback
 */
const ExternalPlayerLauncher: React.FC<ExternalPlayerLauncherProps> = ({
  url, title, playerApp, onClose, onFallbackToWeb,
}) => {
  const [launched, setLaunched] = useState(false);
  const [showFallback, setShowFallback] = useState(false);
  const player = IOS_PLAYERS[playerApp];
  const isiOS = isIOS();

  // Auto-launch on iOS
  useEffect(() => {
    if (isiOS && !launched) {
      setLaunched(true);
      launchExternalPlayer(playerApp, url);

      // Show fallback after timeout
      const timer = setTimeout(() => setShowFallback(true), 3000);
      return () => clearTimeout(timer);
    } else if (!isiOS) {
      setShowFallback(true);
    }
  }, [isiOS, playerApp, url, launched]);

  const handleLaunch = () => {
    launchExternalPlayer(playerApp, url);
    setTimeout(() => setShowFallback(true), 3000);
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 99999,
      background: 'rgba(0,0,0,0.95)', display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center', padding: 24,
    }}>
      {/* Close */}
      <button
        onClick={onClose}
        style={{
          position: 'absolute', top: 16, right: 16,
          background: 'rgba(255,255,255,0.1)', border: 'none', borderRadius: '50%',
          width: 40, height: 40, display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer', color: '#fff',
        }}
      >
        <X size={22} />
      </button>

      {/* Icon */}
      <div style={{
        fontSize: 64, marginBottom: 20, width: 100, height: 100,
        borderRadius: 24, background: 'rgba(255,193,7,0.1)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {player?.icon || '📺'}
      </div>

      <h2 style={{ color: '#fff', fontSize: 22, fontWeight: 700, marginBottom: 8, textAlign: 'center' }}>
        {title}
      </h2>
      <p style={{ color: '#999', fontSize: 14, marginBottom: 24, textAlign: 'center' }}>
        تشغيل عبر {player?.name || playerApp}
      </p>

      {/* Launch button */}
      {isiOS && (
        <button
          onClick={handleLaunch}
          style={{
            padding: '14px 36px', background: '#FFC107', color: '#000',
            border: 'none', borderRadius: 12, fontSize: 17, fontWeight: 700,
            cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10,
            marginBottom: 16, transition: 'transform 0.15s',
          }}
        >
          <ExternalLink size={20} />
          فتح في {player?.name}
        </button>
      )}

      {/* Not iOS message */}
      {!isiOS && (
        <div style={{
          padding: 20, background: 'rgba(255,193,7,0.1)', borderRadius: 12,
          border: '1px solid rgba(255,193,7,0.3)', marginBottom: 20, textAlign: 'center', maxWidth: 400,
        }}>
          <Smartphone size={32} color="#FFC107" style={{ marginBottom: 8 }} />
          <p style={{ color: '#FFC107', fontWeight: 600, marginBottom: 4 }}>
            هذه الميزة متاحة على أجهزة iPhone/iPad
          </p>
          <p style={{ color: '#999', fontSize: 13 }}>
            افتح هذا الرابط من جهاز iOS لتشغيل القناة في {player?.name}
          </p>
        </div>
      )}

      {/* Fallback options */}
      {showFallback && (
        <div style={{
          display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center',
          animation: 'fadeIn 0.3s ease',
        }}>
          <p style={{ color: '#888', fontSize: 13, textAlign: 'center' }}>
            {isiOS ? `لم يفتح التطبيق؟` : ''}
          </p>

          {isiOS && player?.appStoreUrl && (
            <a
              href={player.appStoreUrl}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                padding: '12px 28px', background: 'rgba(255,255,255,0.1)',
                color: '#fff', border: '1px solid rgba(255,255,255,0.2)',
                borderRadius: 10, fontSize: 15, fontWeight: 600,
                textDecoration: 'none', display: 'flex', alignItems: 'center', gap: 8,
              }}
            >
              <Download size={18} />
              تحميل {player.name} من App Store
            </a>
          )}

          {onFallbackToWeb && (
            <button
              onClick={onFallbackToWeb}
              style={{
                padding: '10px 24px', background: 'transparent',
                color: '#FFC107', border: '1px solid rgba(255,193,7,0.4)',
                borderRadius: 10, fontSize: 14, cursor: 'pointer',
              }}
            >
              تشغيل في المتصفح بدلاً من ذلك
            </button>
          )}
        </div>
      )}

      <style>{`
        @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
      `}</style>
    </div>
  );
};

export default ExternalPlayerLauncher;

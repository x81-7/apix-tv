 import React, { useEffect, useRef, useState } from 'react';
 import { X, AlertCircle } from 'lucide-react';
 
 declare global {
   interface Window {
     jwplayer: any;
   }
 }
 
 interface JWPlayerProps {
   url: string;
   title: string;
   drm?: string;
   onClose: () => void;
 }
 
 const JWPLAYER_LIBRARY_URL = 'https://cdn.jwplayer.com/libraries/IDzF9Zmk.js';
 
 const JWPlayer: React.FC<JWPlayerProps> = ({ url, title, drm, onClose }) => {
   const playerRef = useRef<HTMLDivElement>(null);
   const playerInstanceRef = useRef<any>(null);
   const [error, setError] = useState<string | null>(null);
   const [isLoading, setIsLoading] = useState(true);
 
   // Load JWPlayer library
   useEffect(() => {
     const loadJWPlayer = () => {
       return new Promise<void>((resolve, reject) => {
         if (window.jwplayer) {
           resolve();
           return;
         }
 
         const script = document.createElement('script');
         script.src = JWPLAYER_LIBRARY_URL;
         script.async = true;
         script.onload = () => resolve();
         script.onerror = () => reject(new Error('Failed to load JWPlayer library'));
         document.head.appendChild(script);
       });
     };
 
     const initPlayer = async () => {
       try {
         await loadJWPlayer();
 
         if (!playerRef.current || !window.jwplayer) {
           setError('Player initialization failed');
           return;
         }
 
         // Parse DRM if provided (supports combined format KeyID:Key or URL)
         let drmConfig: any = undefined;
         if (drm) {
           if (drm.startsWith('http://') || drm.startsWith('https://')) {
             // URL-based DRM - JWPlayer handles this differently
             drmConfig = {
               clearkey: {
                 keyId: drm // Pass URL, player will fetch
               }
             };
           } else if (drm.includes(':')) {
             // Combined format KeyID:Key
             const [keyId, key] = drm.split(':');
             if (keyId && key) {
               drmConfig = {
                 clearkey: {
                   keyId: keyId,
                   key: key
                 }
               };
             }
           }
         }
 
         // Initialize JWPlayer
         const playerConfig: any = {
           file: url,
           width: '100%',
           height: '100%',
           autostart: true,
           controls: true,
           aspectratio: '16:9',
           stretching: 'uniform',
           mute: false,
           title: title,
           logo: {
             hide: true
           }
         };
 
         if (drmConfig) {
           playerConfig.drm = drmConfig;
         }
 
         playerInstanceRef.current = window.jwplayer(playerRef.current).setup(playerConfig);
 
         playerInstanceRef.current.on('ready', () => {
           setIsLoading(false);
         });
 
         playerInstanceRef.current.on('error', (e: any) => {
           console.error('JWPlayer error:', e);
           setError(`Playback error: ${e.message || 'Unknown error'}`);
           setIsLoading(false);
         });
 
         playerInstanceRef.current.on('setupError', (e: any) => {
           console.error('JWPlayer setup error:', e);
           setError(`Setup error: ${e.message || 'Failed to setup player'}`);
           setIsLoading(false);
         });
 
       } catch (err: any) {
         console.error('Failed to initialize JWPlayer:', err);
         setError(err.message || 'Failed to initialize player');
         setIsLoading(false);
       }
     };
 
     initPlayer();
 
     return () => {
       if (playerInstanceRef.current) {
         try {
           playerInstanceRef.current.remove();
         } catch (e) {
           console.error('Error removing JWPlayer:', e);
         }
         playerInstanceRef.current = null;
       }
     };
   }, [url, title, drm]);
 
   const handleClose = () => {
     if (document.fullscreenElement) {
       document.exitFullscreen();
     }
     onClose();
   };
 
   return (
     <div className="fixed inset-0 z-[9999] bg-black flex flex-col">
       {/* Loading Spinner */}
       {isLoading && (
         <div className="absolute inset-0 z-50 flex items-center justify-center pointer-events-none">
           <div className="w-16 h-16 border-4 border-white/20 border-t-[hsl(45,100%,50%)] rounded-full animate-spin" />
         </div>
       )}
 
       {/* Error State */}
       {error && (
         <div className="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/95 text-white p-4 text-center">
           <AlertCircle className="text-red-600 mb-4" size={64} />
           <p className="text-xl font-bold mb-6">{error}</p>
           <button 
             onClick={handleClose} 
             className="px-8 py-3 bg-white/10 rounded-full hover:bg-white/20 border border-white/10 transition-colors"
           >
             إغلاق
           </button>
         </div>
       )}
 
       {/* Close Button */}
       <button
         onClick={handleClose}
         className="absolute top-4 left-4 z-50 p-2 bg-black/50 rounded-full hover:bg-black/70 transition-colors"
         tabIndex={0}
       >
         <X className="w-6 h-6 text-white" />
       </button>
 
       {/* Title */}
       <div className="absolute top-4 right-4 z-40 text-right">
         <h2 className="text-white text-lg md:text-2xl font-bold tracking-wide drop-shadow-lg">
           {title}
         </h2>
       </div>
 
       {/* JWPlayer Container */}
       <div className="w-full h-full">
         <div ref={playerRef} className="w-full h-full" />
       </div>
     </div>
   );
 };
 
 export default JWPlayer;
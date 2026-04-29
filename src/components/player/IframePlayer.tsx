 import React from 'react';
 import { ArrowLeft } from 'lucide-react';
 
 interface IframePlayerProps {
   url: string;
   title: string;
   onClose: () => void;
 }
 
 const IframePlayer: React.FC<IframePlayerProps> = ({ url, title, onClose }) => {
   const handleClose = () => {
     if (document.fullscreenElement) {
       document.exitFullscreen();
     }
     onClose();
   };
 
   return (
     <div className="fixed inset-0 z-[9999] bg-black flex flex-col">
       {/* Back Button */}
       <button
         onClick={handleClose}
         className="absolute top-4 left-4 z-50 flex items-center gap-2 px-4 py-2 bg-black/70 hover:bg-black/90 rounded-full transition-colors backdrop-blur-sm border border-white/10"
         tabIndex={0}
       >
         <ArrowLeft className="w-5 h-5 text-white" />
         <span className="text-white text-sm font-medium">رجوع</span>
       </button>
 
       {/* Title */}
       <div className="absolute top-4 right-4 z-40 text-right">
         <h2 className="text-white text-lg md:text-xl font-bold tracking-wide drop-shadow-lg bg-black/50 px-3 py-1 rounded-lg backdrop-blur-sm">
           {title}
         </h2>
       </div>
 
       {/* Full-screen Iframe */}
       <iframe
         src={url}
         title={title}
         className="w-full h-full border-0"
         allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
         allowFullScreen
         sandbox="allow-scripts allow-same-origin allow-presentation allow-popups"
       />
     </div>
   );
 };
 
 export default IframePlayer;
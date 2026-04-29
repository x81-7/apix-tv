import React from 'react';

interface IconProps {
  className?: string;
  size?: number;
}

// Play Icon - Modern rounded triangle with gradient effect
export const PlayIcon: React.FC<IconProps> = ({ className = "", size = 32 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="currentColor"
  >
    <defs>
      <linearGradient id="playGradient" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="currentColor" />
        <stop offset="100%" stopColor="currentColor" stopOpacity="0.8" />
      </linearGradient>
    </defs>
    <path 
      d="M8 5.14v14.72a1 1 0 001.52.86l11.38-7.36a1 1 0 000-1.72L9.52 4.28A1 1 0 008 5.14z" 
      fill="url(#playGradient)"
    />
  </svg>
);

// Pause Icon - Modern parallel bars with rounded ends
export const PauseIcon: React.FC<IconProps> = ({ className = "", size = 32 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="currentColor"
  >
    <rect x="6" y="4" width="4" height="16" rx="1.5" />
    <rect x="14" y="4" width="4" height="16" rx="1.5" />
  </svg>
);

// Volume High Icon
export const VolumeHighIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M11 5L6 9H2v6h4l5 4V5z" fill="currentColor" stroke="none" />
    <path d="M15.54 8.46a5 5 0 010 7.07" />
    <path d="M19.07 4.93a10 10 0 010 14.14" />
  </svg>
);

// Volume Low Icon
export const VolumeLowIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M11 5L6 9H2v6h4l5 4V5z" fill="currentColor" stroke="none" />
    <path d="M15.54 8.46a5 5 0 010 7.07" />
  </svg>
);

// Volume Mute Icon
export const VolumeMuteIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M11 5L6 9H2v6h4l5 4V5z" fill="currentColor" stroke="none" />
    <line x1="23" y1="9" x2="17" y2="15" />
    <line x1="17" y1="9" x2="23" y2="15" />
  </svg>
);

// Fullscreen Enter Icon
export const FullscreenIcon: React.FC<IconProps> = ({ className = "", size = 32 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2.5"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M4 8V4h4" />
    <path d="M16 4h4v4" />
    <path d="M4 16v4h4" />
    <path d="M16 20h4v-4" />
  </svg>
);

// Fullscreen Exit Icon
export const FullscreenExitIcon: React.FC<IconProps> = ({ className = "", size = 32 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2.5"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M8 4v4H4" />
    <path d="M16 4v4h4" />
    <path d="M8 20v-4H4" />
    <path d="M16 20v-4h4" />
  </svg>
);

// Audio/Sound Wave Icon
export const AudioIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <rect x="4" y="8" width="3" height="8" rx="1" fill="currentColor" />
    <rect x="10" y="5" width="3" height="14" rx="1" fill="currentColor" />
    <rect x="16" y="10" width="3" height="4" rx="1" fill="currentColor" />
  </svg>
);

// Quality/HD Icon
export const QualityIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="currentColor"
  >
    <rect x="2" y="4" width="20" height="16" rx="3" fill="none" stroke="currentColor" strokeWidth="2" />
    <text x="12" y="15" fontSize="8" fontWeight="bold" textAnchor="middle" fill="currentColor">HD</text>
  </svg>
);

// Settings/Gear Icon
export const SettingsIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z" />
  </svg>
);

// Aspect Ratio Icon (Fit/Fill toggle)
export const AspectRatioIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <rect x="3" y="5" width="18" height="14" rx="2" />
    <path d="M7 9l3 3-3 3" />
    <path d="M17 9l-3 3 3 3" />
  </svg>
);

// Close/X Icon
export const CloseIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2.5"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <line x1="18" y1="6" x2="6" y2="18" />
    <line x1="6" y1="6" x2="18" y2="18" />
  </svg>
);

// Back Arrow Icon
export const BackIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2.5"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M19 12H5" />
    <path d="M12 19l-7-7 7-7" />
  </svg>
);

// Subtitle/CC Icon
export const SubtitleIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="currentColor"
  >
    <rect x="2" y="4" width="20" height="16" rx="2" fill="none" stroke="currentColor" strokeWidth="2" />
    <text x="6" y="13" fontSize="6" fontWeight="bold" fill="currentColor">CC</text>
    <line x1="12" y1="10" x2="18" y2="10" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    <line x1="12" y1="14" x2="18" y2="14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);

// Forward 10s Icon
export const Forward10Icon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="currentColor"
  >
    <path d="M18 13c0 3.31-2.69 6-6 6s-6-2.69-6-6 2.69-6 6-6v4l5-5-5-5v4c-4.42 0-8 3.58-8 8s3.58 8 8 8 8-3.58 8-8h-2z" />
    <text x="9" y="15" fontSize="6" fontWeight="bold">10</text>
  </svg>
);

// Rewind 10s Icon
export const Rewind10Icon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="currentColor"
  >
    <path d="M6 13c0 3.31 2.69 6 6 6s6-2.69 6-6-2.69-6-6-6v4L7 6l5-5v4c4.42 0 8 3.58 8 8s-3.58 8-8 8-8-3.58-8-8h2z" />
    <text x="9" y="15" fontSize="6" fontWeight="bold">10</text>
  </svg>
);

// Check/Tick Icon
export const CheckIcon: React.FC<IconProps> = ({ className = "", size = 20 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="3"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

// Error/Warning Icon
export const ErrorIcon: React.FC<IconProps> = ({ className = "", size = 64 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" />
  </svg>
);

// PiP (Picture in Picture) Icon
export const PipIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <rect x="2" y="4" width="20" height="14" rx="2" />
    <rect x="11" y="10" width="9" height="6" rx="1" fill="currentColor" />
  </svg>
);

// Speed Icon
export const SpeedIcon: React.FC<IconProps> = ({ className = "", size = 24 }) => (
  <svg 
    className={`player-icon ${className}`}
    width={size} 
    height={size} 
    viewBox="0 0 24 24" 
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M12 2a10 10 0 100 20 10 10 0 000-20z" />
    <path d="M12 6v6l4 2" />
  </svg>
);

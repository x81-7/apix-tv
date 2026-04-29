import React from 'react';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Globe, Play, Monitor, Shield, Smartphone } from 'lucide-react';
import PlayerConfigForm from './PlayerConfigForm';
import type { StreamConfig, WebPlayerType, iOSPlayerApp } from '@/types/admin';

interface WebConfigFormProps {
  streamConfig: StreamConfig;
  playerType: WebPlayerType;
  iosPlayerApp?: iOSPlayerApp;
  onStreamChange: (config: StreamConfig) => void;
  onPlayerTypeChange: (playerType: WebPlayerType) => void;
  onIosPlayerAppChange?: (app: iOSPlayerApp) => void;
}

const WebConfigForm: React.FC<WebConfigFormProps> = ({
  streamConfig,
  playerType,
  iosPlayerApp,
  onStreamChange,
  onPlayerTypeChange,
  onIosPlayerAppChange,
}) => {
  return (
    <div className="space-y-4 p-4 rounded-lg border-2 border-blue-600/30 bg-blue-950/20">
      <div className="flex items-center gap-2 text-blue-400">
        <Globe className="w-5 h-5" />
        <span className="font-bold text-base">🌐 إعدادات الويب</span>
      </div>
      
      {/* Player Engine Selection */}
      <div className="space-y-2">
        <Label>محرك التشغيل</Label>
        <Select
          value={playerType}
          onValueChange={(value: WebPlayerType) => onPlayerTypeChange(value)}
        >
          <SelectTrigger className="bg-secondary border-border">
            <SelectValue placeholder="اختر نوع المشغل" />
          </SelectTrigger>
          <SelectContent className="bg-popover border-border z-50">
            <SelectItem value="default">
              <div className="flex items-center gap-2">
                <Play className="w-4 h-4 text-green-500" />
                <span>المشغل الافتراضي (Native)</span>
              </div>
            </SelectItem>
            <SelectItem value="custom">
              <div className="flex items-center gap-2">
                <Globe className="w-4 h-4 text-blue-500" />
                <span>المشغل المخصص (Custom Player)</span>
              </div>
            </SelectItem>
            <SelectItem value="iframe">
              <div className="flex items-center gap-2">
                <Monitor className="w-4 h-4 text-purple-500" />
                <span>Web/Iframe (تضمين مباشر)</span>
              </div>
            </SelectItem>
            <SelectItem value="secure">
              <div className="flex items-center gap-2">
                <Shield className="w-4 h-4 text-amber-500" />
                <span>المشغل الآمن (Secure Player)</span>
              </div>
            </SelectItem>
            <SelectItem value="external_ios">
              <div className="flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-cyan-500" />
                <span>مشغل خارجي iOS</span>
              </div>
            </SelectItem>
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">
          {playerType === 'secure' && '🔒 يمنع استخراج رابط البث - يستخدم HLS.js مع Blob URLs'}
          {playerType === 'external_ios' && '📱 يفتح البث في تطبيق خارجي على iPhone/iPad'}
          {playerType !== 'secure' && playerType !== 'external_ios' && 'المستخدم لن يرى خيار التبديل بين المشغلات'}
        </p>
      </div>

      {/* iOS Player App Selection - only show when external_ios is selected */}
      {playerType === 'external_ios' && (
        <div className="space-y-2 p-3 rounded-lg border border-cyan-600/30 bg-cyan-950/20">
          <Label className="text-cyan-400">تطبيق iOS المشغل</Label>
          <Select
            value={iosPlayerApp || 'vlc'}
            onValueChange={(value: iOSPlayerApp) => onIosPlayerAppChange?.(value)}
          >
            <SelectTrigger className="bg-secondary border-border">
              <SelectValue placeholder="اختر التطبيق" />
            </SelectTrigger>
            <SelectContent className="bg-popover border-border z-50">
              <SelectItem value="vlc">
                <div className="flex items-center gap-2">
                  <span>🎬</span>
                  <span>VLC (مجاني - الأشهر)</span>
                </div>
              </SelectItem>
              <SelectItem value="outplayer">
                <div className="flex items-center gap-2">
                  <span>📺</span>
                  <span>Outplayer (مجاني)</span>
                </div>
              </SelectItem>
              <SelectItem value="infuse">
                <div className="flex items-center gap-2">
                  <span>🎥</span>
                  <span>Infuse (مجاني)</span>
                </div>
              </SelectItem>
              <SelectItem value="kmplayer">
                <div className="flex items-center gap-2">
                  <span>🎞️</span>
                  <span>KMPlayer (مجاني)</span>
                </div>
              </SelectItem>
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            إذا لم يكن التطبيق مثبتاً سيُعرض رابط تحميله + خيار التشغيل في المتصفح
          </p>
        </div>
      )}

      {/* Stream Configuration */}
      <PlayerConfigForm
        streamConfig={streamConfig}
        onChange={onStreamChange}
      />
    </div>
  );
};

export default WebConfigForm;

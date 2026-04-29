import React from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Link, ImageOff } from 'lucide-react';

interface ImageUploaderProps {
  value: string;
  onChange: (url: string) => void;
  label?: string;
}

const ImageUploader: React.FC<ImageUploaderProps> = ({ 
  value, 
  onChange, 
  label = 'رابط الصورة (URL)' 
}) => {
  const [previewError, setPreviewError] = React.useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setPreviewError(false);
    onChange(e.target.value);
  };

  const hasUrl = value.trim() !== '';

  return (
    <div className="space-y-2">
      <Label>{label} (اختياري)</Label>
      
      <div className="relative">
        <Link className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <Input
          type="url"
          value={value}
          onChange={handleChange}
          placeholder="الصق رابط الصورة هنا (https://...)"
          className="pr-10"
          dir="ltr"
        />
      </div>

      {/* Image Preview */}
      {hasUrl && (
        <div className="flex items-center gap-3 p-3 rounded-lg bg-secondary border border-border">
          <div className="w-16 h-16 rounded-lg bg-background flex items-center justify-center overflow-hidden">
            {previewError ? (
              <ImageOff className="w-6 h-6 text-muted-foreground" />
            ) : (
              <img 
                src={value}
                alt="Preview"
                className="w-14 h-14 object-contain"
                onError={() => setPreviewError(true)}
                onLoad={() => setPreviewError(false)}
              />
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm text-foreground font-medium">
              {previewError ? 'فشل تحميل الصورة' : 'معاينة الصورة'}
            </p>
            <p className="text-xs text-muted-foreground truncate" dir="ltr">
              {value.length > 50 ? value.substring(0, 50) + '...' : value}
            </p>
          </div>
        </div>
      )}

      {/* Helper text */}
      <p className="text-xs text-muted-foreground">
        الصق رابط صورة مباشر من الإنترنت. الصورة اختيارية.
      </p>
    </div>
  );
};

export default ImageUploader;

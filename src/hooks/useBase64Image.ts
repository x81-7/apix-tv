import { useState, useCallback } from 'react';

interface UseBase64ImageReturn {
  isConverting: boolean;
  error: string | null;
  convertToBase64: (file: File) => Promise<string>;
}

const MAX_WIDTH = 120;
const JPEG_QUALITY = 0.4;

const compressImage = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    if (!ctx) {
      reject(new Error('فشل في إنشاء سياق الرسم'));
      return;
    }

    img.onload = () => {
      // Calculate new dimensions (max width 120px, maintain aspect ratio)
      let width = img.width;
      let height = img.height;

      if (width > MAX_WIDTH) {
        height = (height * MAX_WIDTH) / width;
        width = MAX_WIDTH;
      }

      canvas.width = width;
      canvas.height = height;

      // Draw resized image
      ctx.drawImage(img, 0, 0, width, height);

      // Convert to JPEG with 0.4 quality
      const compressedBase64 = canvas.toDataURL('image/jpeg', JPEG_QUALITY);
      resolve(compressedBase64);
    };

    img.onerror = () => {
      reject(new Error('فشل في تحميل الصورة'));
    };

    // Load image from file
    const reader = new FileReader();
    reader.onload = (e) => {
      img.src = e.target?.result as string;
    };
    reader.onerror = () => {
      reject(new Error('فشل في قراءة الملف'));
    };
    reader.readAsDataURL(file);
  });
};

export const useBase64Image = (): UseBase64ImageReturn => {
  const [isConverting, setIsConverting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const convertToBase64 = useCallback(async (file: File): Promise<string> => {
    setIsConverting(true);
    setError(null);

    try {
      // Validate file type
      if (!file.type.startsWith('image/')) {
        throw new Error('الملف المحدد ليس صورة صالحة');
      }

      // Validate original file size (max 5MB before compression)
      const maxSize = 5 * 1024 * 1024;
      if (file.size > maxSize) {
        throw new Error('حجم الصورة يجب أن يكون أقل من 5 ميجابايت');
      }

      // Compress and convert to Base64
      const compressedBase64 = await compressImage(file);
      setIsConverting(false);
      return compressedBase64;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'فشل في معالجة الصورة';
      setError(errorMessage);
      setIsConverting(false);
      throw err;
    }
  }, []);

  return { isConverting, error, convertToBase64 };
};


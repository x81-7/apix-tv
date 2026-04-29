import { useState, useEffect } from 'react';

export const useClock = () => {
  const [time, setTime] = useState('');

  useEffect(() => {
    const update = () => {
      const now = new Date();
      let h = now.getHours();
      const m = String(now.getMinutes()).padStart(2, '0');
      h = h % 12;
      h = h ? h : 12;
      setTime(`${h}:${m}`);
    };

    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, []);

  return time;
};

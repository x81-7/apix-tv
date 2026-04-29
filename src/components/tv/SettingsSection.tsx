import React, { useState, useEffect } from 'react';

const SettingsSection: React.FC = () => {
  const [darkMode, setDarkMode] = useState(true);
  const [notificationsEnabled, setNotificationsEnabled] = useState(false);
  const [notificationLoading, setNotificationLoading] = useState(false);

  useEffect(() => {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'light') {
      setDarkMode(false);
      document.body.classList.add('light-mode');
    } else {
      setDarkMode(true);
      document.body.classList.remove('light-mode');
    }
    if ('Notification' in window) {
      setNotificationsEnabled(Notification.permission === 'granted');
    }
  }, []);

  const toggleDarkMode = () => {
    const newValue = !darkMode;
    setDarkMode(newValue);
    if (newValue) {
      document.body.classList.remove('light-mode');
      localStorage.setItem('theme', 'dark');
    } else {
      document.body.classList.add('light-mode');
      localStorage.setItem('theme', 'light');
    }
  };

  const toggleNotifications = async () => {
    if (notificationsEnabled) {
      setNotificationsEnabled(false);
      localStorage.setItem('notifications_enabled', 'false');
      return;
    }
    setNotificationLoading(true);
    try {
      const perm = await Notification.requestPermission();
      if (perm === 'granted') {
        setNotificationsEnabled(true);
        localStorage.setItem('notifications_enabled', 'true');
      }
    } catch (error) {
      console.error('Failed to enable notifications:', error);
    } finally {
      setNotificationLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '600px', margin: '0 auto' }}>
      <div className="setting-row" tabIndex={0} onClick={toggleDarkMode}>
        <span style={{ fontWeight: 'bold' }}>Dark Mode</span>
        <label className="ios-switch">
          <input type="checkbox" checked={darkMode} onChange={() => {}} />
          <span className="slider" />
        </label>
      </div>
      
      <div 
        className="setting-row" 
        tabIndex={0} 
        onClick={toggleNotifications}
        style={{ opacity: notificationLoading ? 0.7 : 1 }}
      >
        <span style={{ fontWeight: 'bold' }}>
          Notifications
          {notificationLoading && <span style={{ marginRight: '8px', fontSize: '12px' }}> جاري التفعيل...</span>}
        </span>
        <label className="ios-switch">
          <input 
            type="checkbox" 
            checked={notificationsEnabled} 
            onChange={() => {}}
            disabled={notificationLoading}
          />
          <span className="slider" />
        </label>
      </div>

      <div className="setting-row" tabIndex={0}>
        <span style={{ fontWeight: 'bold' }}>Telegram Channel</span>
        <span style={{ color: 'hsl(var(--gold))' }}>❯</span>
      </div>
      <div className="setting-row" tabIndex={0}>
        <span style={{ fontWeight: 'bold' }}>Contact Us</span>
        <span style={{ color: 'hsl(var(--gold))' }}>❯</span>
      </div>
    </div>
  );
};

export default SettingsSection;

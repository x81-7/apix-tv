import React from 'react';

interface NavItem {
  id: string;
  name: string;
  icon: React.ReactNode;
}

interface BottomNavProps {
  navItems: NavItem[];
  activeId: string;
  onSelect: (id: string) => void;
}

const BottomNav: React.FC<BottomNavProps> = ({ navItems, activeId, onSelect }) => {
  return (
    <nav className="bottom-nav">
      {navItems.map((item) => (
        <button
          key={item.id}
          className={`nav-item ${activeId === item.id ? 'active' : ''}`}
          onClick={() => onSelect(item.id)}
          tabIndex={0}
        >
          {item.icon}
          <span>{item.name}</span>
        </button>
      ))}
    </nav>
  );
};

export default BottomNav;

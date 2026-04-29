import React from 'react';

interface NavItem {
  id: string;
  name: string;
  icon: React.ReactNode;
}

interface SidebarProps {
  navItems: NavItem[];
  activeId: string;
  onSelect: (id: string) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ navItems, activeId, onSelect }) => {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="logo-text">
          <span className="logo-span">APi</span>X
        </div>
      </div>
      
      <div className="menu-list">
        {navItems.map((item) => (
          <button
            key={item.id}
            className={`menu-btn ${activeId === item.id ? 'active' : ''}`}
            onClick={() => onSelect(item.id)}
            tabIndex={0}
          >
            <span>{item.name}</span>
            {item.icon}
          </button>
        ))}
      </div>
    </aside>
  );
};

export default Sidebar;

import React, { useRef, useEffect } from 'react';

interface SearchOverlayProps {
  visible: boolean;
  value: string;
  onChange: (value: string) => void;
  onSearch: () => void;
  onClose: () => void;
}

const SearchOverlay: React.FC<SearchOverlayProps> = ({ visible, value, onChange, onSearch, onClose }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (visible && inputRef.current) {
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [visible]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onSearch();
    }
  };

  return (
    <div className={`search-overlay ${visible ? 'visible' : ''}`} id="searchOverlay">
      <div className="search-header">
        <h3>Search</h3>
        <button onClick={onClose} className="search-cancel-btn">
          Cancel
        </button>
      </div>
      <input
        ref={inputRef}
        type="text"
        className="search-input"
        placeholder="Type channel name..."
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
      />
    </div>
  );
};

export default SearchOverlay;

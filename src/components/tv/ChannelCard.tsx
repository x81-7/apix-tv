import React from 'react';

interface ChannelCardProps {
  name: string;
  imageUrl: string;
  onClick: () => void;
  tabIndex?: number;
  dataName?: string;
}

const ChannelCard: React.FC<ChannelCardProps> = ({ name, imageUrl, onClick, tabIndex = 0, dataName }) => {
  return (
    <div
      className="channel-card"
      tabIndex={tabIndex}
      data-name={dataName || name.toLowerCase()}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onClick();
      }}
      role="button"
    >
      <img 
        src={imageUrl || 'https://via.placeholder.com/300x170?text=TV'} 
        alt={name}
        onError={(e) => {
          (e.target as HTMLImageElement).src = 'https://via.placeholder.com/300x170?text=TV';
        }}
      />
      <div className="card-overlay">
        <span className="card-title">{name}</span>
      </div>
    </div>
  );
};

export default ChannelCard;

import React from 'react';

interface LoaderProps {
  type: 'startup' | 'content';
  visible: boolean;
}

const Loader: React.FC<LoaderProps> = ({ type, visible }) => {
  if (type === 'startup') {
    return (
      <div
        id="startup-loader"
        style={{
          opacity: visible ? 1 : 0,
          display: visible ? 'flex' : 'none',
          transition: 'opacity 0.3s ease',
        }}
      >
        <div className="spinner" />
      </div>
    );
  }

  return (
    <div
      id="content-loader"
      style={{
        opacity: visible ? 1 : 0,
        display: visible ? 'flex' : 'none',
        transition: 'opacity 0.2s ease',
      }}
    >
      <div className="spinner" />
    </div>
  );
};

export default Loader;

import { useEffect, useCallback } from 'react';

interface UseTVNavigationProps {
  isSearchOpen: boolean;
  onCloseSearch: () => void;
}

export const useTVNavigation = ({ isSearchOpen, onCloseSearch }: UseTVNavigationProps) => {
  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    const key = e.key;
    const searchInput = document.getElementById('searchInput') as HTMLInputElement | null;
    const overlayOpen = isSearchOpen;

    // Handle "Back" / "Escape" (Closes Search)
    if (key === 'Escape' || key === 'BrowserBack') {
      if (overlayOpen) {
        e.preventDefault();
        onCloseSearch();
        return;
      }
    }

    // Handle "Backspace" (NEVER Close Search when typing)
    if (key === 'Backspace' && document.activeElement === searchInput) {
      return;
    }

    if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter'].includes(key)) return;
    if (overlayOpen) return;

    // === Standard Navigation Logic ===
    const activeEl = document.activeElement as HTMLElement;
    const isSidebar = activeEl?.classList.contains('menu-btn');
    const isGrid = activeEl?.classList.contains('channel-card') || activeEl?.classList.contains('setting-row');
    const isTopBar = activeEl?.classList.contains('tv-search-icon');

    if (key === 'ArrowRight') {
      if (isSidebar) return;
      else if (isGrid) {
        const grid = activeEl.parentElement;
        if (!grid) return;
        const cards = Array.from(grid.querySelectorAll('.channel-card, .setting-row')).filter(
          (c) => (c as HTMLElement).style.display !== 'none'
        ) as HTMLElement[];
        const index = cards.indexOf(activeEl);
        const cardWidth = activeEl.offsetWidth;
        const gridWidth = grid.offsetWidth;
        const columns = Math.floor(gridWidth / cardWidth) || 1;

        if (activeEl.previousElementSibling === null || index % columns === 0) {
          const activeSidebarBtn = document.querySelector('.menu-btn.active') as HTMLElement;
          if (activeSidebarBtn) activeSidebarBtn.focus();
        } else {
          const prev = activeEl.previousElementSibling as HTMLElement;
          if (prev) prev.focus();
        }
      }
    } else if (key === 'ArrowLeft') {
      if (isSidebar) {
        const firstCard = document.querySelector('.section.active .channel-card, .section.active .setting-row') as HTMLElement;
        if (firstCard) firstCard.focus();
      } else if (isGrid) {
        const grid = activeEl.parentElement;
        if (!grid) return;
        const cards = Array.from(grid.querySelectorAll('.channel-card, .setting-row')).filter(
          (c) => (c as HTMLElement).style.display !== 'none'
        ) as HTMLElement[];
        const index = cards.indexOf(activeEl);
        const cardWidth = activeEl.offsetWidth;
        const gridWidth = grid.offsetWidth;
        const columns = Math.floor(gridWidth / cardWidth) || 1;

        if ((index + 1) % columns === 0 || index === cards.length - 1) {
          e.preventDefault();
        } else {
          const next = activeEl.nextElementSibling as HTMLElement;
          if (next) next.focus();
        }
      }
    } else if (key === 'ArrowUp') {
      e.preventDefault();
      if (isGrid) {
        const grid = activeEl.parentElement;
        if (!grid) return;
        const cards = Array.from(grid.querySelectorAll('.channel-card, .setting-row')).filter(
          (c) => (c as HTMLElement).style.display !== 'none'
        ) as HTMLElement[];
        const index = cards.indexOf(activeEl);
        const cardWidth = activeEl.offsetWidth;
        const gridWidth = grid.offsetWidth;
        const columns = Math.floor(gridWidth / cardWidth) || 1;

        if (index < columns) {
          const searchIcon = document.querySelector('.tv-search-icon') as HTMLElement;
          if (searchIcon) searchIcon.focus();
        } else {
          if (cards[index - columns]) cards[index - columns].focus();
        }
      } else if (isSidebar) {
        const prev = activeEl.previousElementSibling as HTMLElement;
        if (prev) prev.focus();
        else {
          const searchIcon = document.querySelector('.tv-search-icon') as HTMLElement;
          if (searchIcon) searchIcon.focus();
        }
      }
    } else if (key === 'ArrowDown') {
      e.preventDefault();
      if (isTopBar) {
        const activeSidebar = document.querySelector('.menu-btn.active') as HTMLElement;
        if (activeSidebar) activeSidebar.focus();
      } else if (isGrid) {
        const grid = activeEl.parentElement;
        if (!grid) return;
        const cards = Array.from(grid.querySelectorAll('.channel-card, .setting-row')).filter(
          (c) => (c as HTMLElement).style.display !== 'none'
        ) as HTMLElement[];
        const index = cards.indexOf(activeEl);
        const cardWidth = activeEl.offsetWidth;
        const gridWidth = grid.offsetWidth;
        const columns = Math.floor(gridWidth / cardWidth) || 1;

        if (cards[index + columns]) cards[index + columns].focus();
      } else if (isSidebar) {
        const next = activeEl.nextElementSibling as HTMLElement;
        if (next) next.focus();
      }
    } else if (key === 'Enter') {
      if (isGrid || isSidebar) {
        activeEl.click();
      }
    }
  }, [isSearchOpen, onCloseSearch]);

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);
};

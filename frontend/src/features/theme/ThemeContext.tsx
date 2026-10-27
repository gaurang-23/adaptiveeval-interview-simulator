import React, { createContext, useContext, useEffect, useState } from 'react';
import { Moon, Sun } from 'lucide-react';
import { api, storage } from '../../services/api';

export type ThemeMode = 'dark' | 'light';

export interface ChartThemeTokens {
  grid: string;
  axisText: string;
  gridStroke: string;
  axisStroke: string;
  axisTickFill: string;
  tooltipBg: string;
  tooltipBorder: string;
  tooltipText: string;
}

interface ThemeContextValue {
  theme: ThemeMode;
  setTheme: (theme: ThemeMode) => void;
  toggleTheme: () => void;
  chartColors: ChartThemeTokens;
}

export const THEME_STORAGE_KEY = 'atis_theme';

function resolveInitialTheme(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'dark';
  }
  const saved = localStorage.getItem(THEME_STORAGE_KEY);
  if (saved === 'dark' || saved === 'light') {
    return saved;
  }
  const userPref = storage.getUser()?.themePreference?.toLowerCase();
  if (userPref === 'dark' || userPref === 'light') {
    return userPref;
  }
  if (typeof window.matchMedia === 'function') {
    try {
      if (window.matchMedia('(prefers-color-scheme: light)').matches) {
        return 'light';
      }
    } catch {
      // ignore matchMedia errors in test environments
    }
  }
  return 'dark';
}

function applyThemeToDom(theme: ThemeMode) {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.setAttribute('data-theme', theme);
  root.classList.remove('dark', 'light', 'theme-dark', 'theme-light');
  root.classList.add(theme, `theme-${theme}`);
  if (document.body) {
    document.body.setAttribute('data-theme', theme);
  }
}

const DARK_CHART_COLORS: ChartThemeTokens = {
  grid: '#1e293b',
  axisText: '#94a3b8',
  gridStroke: '#1e293b',
  axisStroke: '#64748b',
  axisTickFill: '#94a3b8',
  tooltipBg: '#090d16',
  tooltipBorder: '#1e293b',
  tooltipText: '#f1f5f9',
};

const LIGHT_CHART_COLORS: ChartThemeTokens = {
  grid: '#e2e8f0',
  axisText: '#475569',
  gridStroke: '#e2e8f0',
  axisStroke: '#64748b',
  axisTickFill: '#475569',
  tooltipBg: '#ffffff',
  tooltipBorder: '#cbd5e1',
  tooltipText: '#0f172a',
};

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [theme, setThemeState] = useState<ThemeMode>(() => {
    const initial = resolveInitialTheme();
    applyThemeToDom(initial);
    return initial;
  });

  useEffect(() => {
    applyThemeToDom(theme);
  }, [theme]);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    let mediaQuery: MediaQueryList;
    try {
      mediaQuery = window.matchMedia('(prefers-color-scheme: light)');
    } catch {
      return;
    }

    const handleSystemChange = (e: MediaQueryListEvent) => {
      // Do not overwrite an explicitly saved preference when OS theme changes
      const explicitSaved = localStorage.getItem(THEME_STORAGE_KEY);
      if (explicitSaved === 'dark' || explicitSaved === 'light') {
        return;
      }
      setThemeState(e.matches ? 'light' : 'dark');
    };

    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', handleSystemChange);
      return () => mediaQuery.removeEventListener('change', handleSystemChange);
    }
  }, []);

  const setTheme = (nextTheme: ThemeMode) => {
    localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    applyThemeToDom(nextTheme);
    setThemeState(nextTheme);

    if (storage.getToken()) {
      const currentUser = storage.getUser();
      if (currentUser) {
        storage.setUser({ ...currentUser, themePreference: nextTheme });
      }
      api.users.updateTheme(nextTheme).catch(() => {});
    }
  };

  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  };

  const chartColors = theme === 'light' ? LIGHT_CHART_COLORS : DARK_CHART_COLORS;

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggleTheme, chartColors }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = (): ThemeContextValue => {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return ctx;
};

export const ThemeToggle: React.FC<{ compact?: boolean; className?: string }> = ({
  compact = false,
  className = '',
}) => {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      data-testid="theme-toggle-btn"
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      title={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      className={`inline-flex items-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700/80 bg-white dark:bg-[#0d1321] hover:bg-slate-100 dark:hover:bg-slate-800/70 px-2.5 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${className}`}
    >
      {isDark ? (
        <>
          <Sun className="w-3.5 h-3.5 text-amber-400 shrink-0" />
          {!compact && <span>Light Mode</span>}
        </>
      ) : (
        <>
          <Moon className="w-3.5 h-3.5 text-blue-600 shrink-0" />
          {!compact && <span>Dark Mode</span>}
        </>
      )}
    </button>
  );
};

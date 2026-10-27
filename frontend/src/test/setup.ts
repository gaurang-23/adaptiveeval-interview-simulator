import React from 'react';
import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock ResizeObserver for Recharts in JSDOM
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

window.ResizeObserver = ResizeObserverMock;

// Mock Monaco Editor in JSDOM environment so workspace tests can type into it directly
vi.mock('@monaco-editor/react', () => ({
  default: ({
    value,
    onChange,
  }: {
    value: string;
    onChange?: (val: string) => void;
  }) => {
    return React.createElement('textarea', {
      'data-testid': 'monaco-editor-mock',
      value: value || '',
      onChange: (e: { target: { value: string } }) => onChange && onChange(e.target.value),
    });
  },
}));

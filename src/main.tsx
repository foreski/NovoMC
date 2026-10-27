import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { installDevMock } from './lib/devMock';
import './index.css';

// In a plain browser during development, fake the Rust side with sample data
// (dropped from release builds, where import.meta.env.DEV is false).
if (import.meta.env.DEV && !('__TAURI_INTERNALS__' in window)) {
  installDevMock();
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

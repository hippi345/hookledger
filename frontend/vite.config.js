import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/invoice-desk/',
  build: {
    outDir: '../src/main/resources/static/invoice-desk',
    emptyOutDir: true,
  },
});

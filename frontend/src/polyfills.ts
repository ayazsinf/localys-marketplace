// Polyfills for packages expecting Node globals in the browser.
(globalThis as any).global = globalThis;

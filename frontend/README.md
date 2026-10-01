# Flowpanel frontend

Next.js (App Router) app: marketing landing page (`/`), demo login (`/login`) and the product (`/app`).

```bash
npm install
npm run dev        # http://localhost:3000, expects the backend on BACKEND_URL (default http://localhost:8080)
npm run gen:api    # regenerate src/lib/api/schema.d.ts from the running backend's OpenAPI spec
npm run lint && npm run typecheck && npm run test && npm run build
npm run test:e2e   # Playwright smoke tests (backend running in mock profile, after npm run build)
```

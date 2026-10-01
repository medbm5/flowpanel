import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // The browser only ever talks to /api on the frontend origin, so the session cookie stays first-party.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/:path*` }];
  },
  poweredByHeader: false,
};

export default nextConfig;

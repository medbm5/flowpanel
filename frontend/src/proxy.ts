import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const SESSION_COOKIE = "fp_session";

/**
 * Optimistic auth check for the product app: no session cookie → login page. The backend validates the JWT on every
 * API call, so this only avoids rendering an empty app shell.
 */
export function proxy(request: NextRequest) {
  if (!request.cookies.get(SESSION_COOKIE)?.value) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", request.nextUrl.pathname);
    return NextResponse.redirect(login);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/app", "/app/:path*"],
};

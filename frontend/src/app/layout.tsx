import type { Metadata, Viewport } from "next";
import { Schibsted_Grotesk } from "next/font/google";
import { ThemeProvider } from "@/components/theme-provider";
import "./globals.css";

const schibsted = Schibsted_Grotesk({
  variable: "--font-schibsted",
  subsets: ["latin"],
  // "optional" avoids a late font swap repainting the hero headline (LCP).
  display: "optional",
});

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Flowpanel — temporary staffing, from the first email to the last invoice",
    template: "%s · Flowpanel",
  },
  description:
    "A phase-gated workflow for temporary staffing missions with an accountable AI layer: the AI drafts, rules decide, a person approves.",
  applicationName: "Flowpanel",
  openGraph: {
    type: "website",
    siteName: "Flowpanel",
    title: "Flowpanel — from the first email to the last invoice, one workflow",
    description: "Intake, sourcing, contracts, timesheets and invoices for temporary staffing, with AI that stays accountable.",
  },
  robots: { index: true, follow: true },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f6f7f9" },
    { media: "(prefers-color-scheme: dark)", color: "#0a0f1e" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${schibsted.variable} h-full antialiased`} suppressHydrationWarning>
      <body className="min-h-full flex flex-col">
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  );
}

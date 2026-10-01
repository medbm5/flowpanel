import { ImageResponse } from "next/og";

export const alt = "Flowpanel — from the first email to the last invoice, one workflow";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  const segments = [1, 1, 1, 0.5, 0.15, 0.15];
  return new ImageResponse(
    (
      <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column", justifyContent: "space-between", padding: 72, background: "#f6f7f9", color: "#0f172a", fontFamily: "sans-serif" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 16, fontSize: 34, fontWeight: 600 }}>
          <div style={{ width: 48, height: 48, borderRadius: 14, background: "#2f4bd6" }} />
          Flowpanel
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 28 }}>
          <div style={{ fontSize: 76, fontWeight: 700, lineHeight: 1.04, letterSpacing: -2, maxWidth: 980 }}>
            From the first email to the last invoice, one workflow.
          </div>
          <div style={{ display: "flex", gap: 10, width: 520 }}>
            {segments.map((o, i) => (
              <div key={i} style={{ flex: 1, height: 12, borderRadius: 6, background: "#2f4bd6", opacity: o }} />
            ))}
          </div>
        </div>
        <div style={{ fontSize: 26, color: "#56607a" }}>Temporary staffing · AI drafts, rules decide, a person approves</div>
      </div>
    ),
    size,
  );
}

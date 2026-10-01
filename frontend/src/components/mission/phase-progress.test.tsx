import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { phases } from "@/test/fixtures";
import { PhaseProgress } from "./phase-progress";

describe("PhaseProgress", () => {
  it("renders one segment per phase with its state", () => {
    const { container } = render(<PhaseProgress phases={phases(3)} />);
    const segments = container.querySelectorAll("[data-state]");
    expect(segments).toHaveLength(6);
    expect([...segments].map((s) => s.getAttribute("data-state"))).toEqual(["DONE", "DONE", "DONE", "ACTIVE", "LOCKED", "LOCKED"]);
  });

  it("describes the active phase for assistive technology", () => {
    render(<PhaseProgress phases={phases(1)} />);
    expect(screen.getByRole("img", { name: "Phase 2 of 6: Sourcing" })).toBeInTheDocument();
  });
});

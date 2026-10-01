import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { detail, phases } from "@/test/fixtures";
import { renderWithQuery } from "@/test/render";
import { GatePanel } from "./gate-panel";
import { PhaseRail } from "./phase-rail";

const info = vi.fn();
vi.mock("sonner", () => ({ toast: { info: (...a: unknown[]) => info(...a), success: vi.fn(), error: vi.fn() } }));

describe("GatePanel", () => {
  it("keeps Finalize disabled until every check passes", () => {
    const mission = detail({
      gate: {
        phase: "INTAKE",
        ready: false,
        checks: [
          { id: "order-extracted", label: "Order extracted from the email", passed: true, action: "", needsReview: false },
          { id: "fields-reviewed", label: "1 field(s) to review", passed: false, action: "Review 1 flagged field", needsReview: true },
        ],
      },
      nextAction: "Review 1 flagged field",
    });
    renderWithQuery(<GatePanel mission={mission} />);
    expect(screen.getByTestId("finalize")).toBeDisabled();
    expect(screen.getByText("1 field(s) to review")).toBeInTheDocument();
    expect(screen.getByText("Next: Review 1 flagged field")).toBeInTheDocument();
  });

  it("enables Finalize when the gate is ready", () => {
    const mission = detail({
      gate: { phase: "INTAKE", ready: true, checks: [{ id: "a", label: "Done", passed: true, action: "", needsReview: false }] },
    });
    renderWithQuery(<GatePanel mission={mission} />);
    expect(screen.getByTestId("finalize")).toBeEnabled();
    expect(screen.getByTestId("finalize")).toHaveTextContent("Finalize intake");
  });
});

describe("PhaseRail", () => {
  it("opens done and active phases, and names the phase to finalize when a locked one is clicked", () => {
    const onSelect = vi.fn();
    render(<PhaseRail phases={phases(2)} current="CONTRACTS" selected="CONTRACTS" onSelect={onSelect} />);
    fireEvent.click(screen.getByTestId("rail-INTAKE"));
    expect(onSelect).toHaveBeenCalledWith("INTAKE");

    fireEvent.click(screen.getByTestId("rail-INVOICE"));
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(info).toHaveBeenCalledWith("Invoice is locked", { description: "Finalize Contracts first." });
  });
});

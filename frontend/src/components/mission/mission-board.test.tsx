import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { detail, stubFetch, summary } from "@/test/fixtures";
import { renderWithQuery } from "@/test/render";
import { MissionBoard } from "./mission-board";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));

const TEMPLATES = [
  { code: "forklift-lille", title: "Caristes CACES 3 — Lille Lesquin", site: "Entrepôt Lille Lesquin", description: "2 forklift operators", emailText: "" },
];

describe("MissionBoard", () => {
  beforeEach(() => push.mockReset());

  it("shows one row per mission with phase progress, positions, next action and the review tag", async () => {
    stubFetch({
      "GET /missions": () => [
        summary(),
        summary({ id: 2, ref: "ORD-2026-0147", title: "Préparateurs de commandes — Roubaix", needsReview: true, positions: { filled: 0, total: 3 } }),
      ],
    });
    renderWithQuery(<MissionBoard />);
    const rows = await screen.findAllByTestId("mission-row");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("ORD-2026-0142")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Run the timesheet checks")).toBeInTheDocument();
    expect(within(rows[0]).queryByText("Needs review")).not.toBeInTheDocument();
    expect(within(rows[1]).getByText("Needs review")).toBeInTheDocument();
    expect(within(rows[1]).getByText(/0\/3/)).toBeInTheDocument();
  });

  it("shows an empty state with a call to action", async () => {
    stubFetch({ "GET /missions": () => [] });
    renderWithQuery(<MissionBoard />);
    expect(await screen.findByText(/No mission yet/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /New mission/ }).length).toBeGreaterThan(0);
  });

  it("shows the ProblemDetail message when loading fails", async () => {
    globalThis.fetch = (async () =>
      new Response(JSON.stringify({ title: "Internal error", detail: "Unexpected server error" }), { status: 500 })) as typeof fetch;
    renderWithQuery(<MissionBoard />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Unexpected server error");
  });

  it("creating a mission adds a row without reloading the page", async () => {
    const created = detail();
    stubFetch({
      "GET /missions": () => [summary()],
      "GET /request-templates": () => TEMPLATES,
      "POST /missions": () => created,
    });
    renderWithQuery(<MissionBoard />);
    expect(await screen.findAllByTestId("mission-row")).toHaveLength(1);

    fireEvent.click(screen.getByTestId("new-mission"));
    fireEvent.click(await screen.findByTestId("template-forklift-lille"));
    fireEvent.click(screen.getByTestId("create-mission"));

    await waitFor(() => expect(screen.getAllByTestId("mission-row")).toHaveLength(2));
    expect(screen.getByText("ORD-2026-0150")).toBeInTheDocument();
    expect(push).toHaveBeenCalledWith("/app/missions/99");
  });
});

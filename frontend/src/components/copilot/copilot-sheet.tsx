"use client";

import { useMutation } from "@tanstack/react-query";
import { Bot, ChevronDown, FileText, Loader2, MessageSquareText, SendHorizontal, Wrench } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { AiBadge } from "@/components/mission/status-badges";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { api, call, errorMessage } from "@/lib/api/client";
import type { Citation, CopilotAnswer, ToolCallView } from "@/lib/api/types";
import { cn } from "@/lib/utils";

type Turn = { id: number; question: string; answer?: CopilotAnswer; error?: string };

const SUGGESTIONS = [
  "Which missions need review?",
  "What is the night work bonus?",
  "How many open timesheet anomalies are there?",
  "What is our spend by supplier?",
];

/** Copilot: answers from tools and documents only, with numbered citations and the trace of tool calls. */
export function CopilotSheet() {
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [source, setSource] = useState<Citation | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const nextId = useRef(1);
  const ask = useMutation({ mutationFn: (q: string) => call<CopilotAnswer>(api.POST("/copilot/ask", { body: { question: q } })) });

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [turns]);

  function submit(q: string) {
    const text = q.trim();
    if (!text || ask.isPending) return;
    const id = nextId.current++;
    setTurns((t) => [...t, { id, question: text }]);
    setQuestion("");
    ask.mutate(text, {
      onSuccess: (answer) => setTurns((t) => t.map((x) => (x.id === id ? { ...x, answer } : x))),
      onError: (e) => setTurns((t) => t.map((x) => (x.id === id ? { ...x, error: errorMessage(e) } : x))),
    });
  }

  return (
    <>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetTrigger asChild>
          <Button variant="outline" size="sm" data-testid="open-copilot">
            <MessageSquareText aria-hidden /> <span className="hidden sm:inline">Copilot</span>
          </Button>
        </SheetTrigger>
        <SheetContent className="flex w-full flex-col gap-0 p-0 sm:max-w-md">
          <SheetHeader className="border-b p-4">
            <SheetTitle className="flex items-center gap-2">
              <Bot className="size-4 text-cobalt" aria-hidden /> Copilot <AiBadge />
            </SheetTitle>
            <SheetDescription>Answers come from your data and documents only, with sources. It cannot see other organizations.</SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto p-4" aria-live="polite">
            {turns.length === 0 && (
              <div className="grid gap-2">
                <p className="text-sm text-muted-foreground">Try one of these:</p>
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => submit(s)}
                    className="rounded-lg border px-3 py-2 text-left text-sm transition-colors hover:bg-accent/40"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
            <ol className="grid gap-5">
              {turns.map((t) => (
                <li key={t.id} className="grid gap-2">
                  <p className="ml-auto max-w-[85%] rounded-2xl rounded-br-sm bg-cobalt px-3 py-2 text-sm text-primary-foreground">{t.question}</p>
                  {!t.answer && !t.error && (
                    <p className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Loader2 className="size-4 animate-spin" aria-hidden /> Thinking…
                    </p>
                  )}
                  {t.error && (
                    <p role="alert" className="rounded-lg border border-bad/30 bg-bad-soft px-3 py-2 text-sm text-bad">
                      {t.error}
                    </p>
                  )}
                  {t.answer && <AnswerBlock answer={t.answer} onSource={setSource} />}
                </li>
              ))}
            </ol>
            <div ref={endRef} />
          </div>
          <form
            className="flex items-end gap-2 border-t p-3"
            onSubmit={(e) => {
              e.preventDefault();
              submit(question);
            }}
          >
            <Textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  submit(question);
                }
              }}
              rows={2}
              placeholder="Ask about missions, spend, anomalies or your policies…"
              aria-label="Question for the copilot"
              className="min-h-0 resize-none"
              data-testid="copilot-input"
            />
            <Button type="submit" size="icon" aria-label="Send" disabled={!question.trim() || ask.isPending}>
              <SendHorizontal aria-hidden />
            </Button>
          </form>
        </SheetContent>
      </Sheet>
      <Dialog open={!!source} onOpenChange={(v) => !v && setSource(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <span className="grid size-6 place-items-center rounded-md bg-cobalt-soft text-xs font-semibold text-cobalt">{source?.n}</span>
              {source?.documentTitle}
            </DialogTitle>
            <DialogDescription>{source?.heading ? `Section: ${source.heading}` : "Source passage"}</DialogDescription>
          </DialogHeader>
          <blockquote className="whitespace-pre-wrap border-l-2 border-cobalt pl-3 text-sm leading-relaxed">{source?.excerpt}</blockquote>
        </DialogContent>
      </Dialog>
    </>
  );
}

function AnswerBlock({ answer, onSource }: { answer: CopilotAnswer; onSource: (c: Citation) => void }) {
  const [showSteps, setShowSteps] = useState(false);
  return (
    <div className="grid gap-2" data-testid="copilot-answer">
      <div className={cn("max-w-[95%] rounded-2xl rounded-bl-sm border bg-card px-3 py-2 text-sm leading-relaxed", answer.notFound && "text-muted-foreground")}>
        {renderWithCitations(answer.answer, answer.citations, onSource)}
      </div>
      {answer.citations.length > 0 && (
        <div className="flex flex-wrap gap-1.5" aria-label="Sources">
          {answer.citations.map((c) => (
            <button
              key={c.n}
              type="button"
              onClick={() => onSource(c)}
              className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs transition-colors hover:border-cobalt/50 hover:bg-accent/40"
              data-testid="citation-chip"
            >
              <span className="font-semibold text-cobalt">{c.n}</span>
              <FileText className="size-3 text-muted-foreground" aria-hidden />
              <span className="max-w-40 truncate">{c.documentTitle}</span>
            </button>
          ))}
        </div>
      )}
      {answer.toolCalls.length > 0 && (
        <div className="rounded-lg border bg-muted/40 text-xs">
          <button
            type="button"
            onClick={() => setShowSteps((v) => !v)}
            aria-expanded={showSteps}
            className="flex w-full items-center justify-between px-3 py-1.5 text-muted-foreground"
            data-testid="copilot-steps"
          >
            <span className="flex items-center gap-1.5">
              <Wrench className="size-3" aria-hidden /> {answer.toolCalls.length} step{answer.toolCalls.length === 1 ? "" : "s"}
            </span>
            <ChevronDown className={cn("size-3.5 transition-transform", showSteps && "rotate-180")} aria-hidden />
          </button>
          {showSteps && (
            <ol className="grid gap-2 border-t px-3 py-2">
              {answer.toolCalls.map((t, i) => (
                <ToolStep key={i} call={t} />
              ))}
            </ol>
          )}
        </div>
      )}
    </div>
  );
}

function ToolStep({ call: t }: { call: ToolCallView }) {
  const result = JSON.stringify(t.result, null, 1);
  return (
    <li>
      <p className="font-mono font-medium">
        {t.name}({t.arguments && Object.keys(t.arguments as object).length ? JSON.stringify(t.arguments) : ""})
      </p>
      <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap break-all text-muted-foreground">
        {result.length > 1200 ? `${result.slice(0, 1200)}…` : result}
      </pre>
    </li>
  );
}

/** Turns "[n]" markers into buttons that open the cited passage. */
function renderWithCitations(text: string, citations: Citation[], onSource: (c: Citation) => void) {
  const parts = text.split(/(\[\d{1,2}\])/g);
  return parts.map((part, i) => {
    const m = part.match(/^\[(\d{1,2})\]$/);
    const citation = m ? citations.find((c) => c.n === Number(m[1])) : undefined;
    if (!citation) return <span key={i}>{part}</span>;
    return (
      <button
        key={i}
        type="button"
        onClick={() => onSource(citation)}
        className="mx-0.5 inline-grid size-5 place-items-center rounded bg-cobalt-soft align-text-top text-[11px] font-semibold text-cobalt"
        aria-label={`Source ${citation.n}: ${citation.documentTitle}`}
      >
        {citation.n}
      </button>
    );
  });
}

"use client";

import { FileText, Loader2, Mail, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { errorMessage } from "@/lib/api/client";
import { useCreateMission, useTemplates } from "@/lib/missions";
import { cn } from "@/lib/utils";

export function NewMissionDialog({ trigger }: { trigger?: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<"template" | "email">("template");
  const [template, setTemplate] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const templates = useTemplates();
  const create = useCreateMission();
  const router = useRouter();

  const canSubmit = mode === "template" ? !!template : email.trim().length > 20;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    create.mutate(mode === "template" ? { templateCode: template! } : { emailText: email.trim() }, {
      onSuccess: (mission) => {
        toast.success(`${mission.ref} created`, { description: "Opening it at Intake." });
        setOpen(false);
        setTemplate(null);
        setEmail("");
        router.push(`/app/missions/${mission.id}`);
      },
      onError: (e) => toast.error("Could not create the mission", { description: errorMessage(e) }),
    });
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {trigger ?? (
          <Button data-testid="new-mission">
            <Plus aria-hidden /> New mission
          </Button>
        )}
      </DialogTrigger>
      <DialogContent className="sm:max-w-xl">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>New mission</DialogTitle>
            <DialogDescription>Start from a request template, or paste the email you received from the site.</DialogDescription>
          </DialogHeader>
          <Tabs value={mode} onValueChange={(v) => setMode(v as "template" | "email")} className="mt-4">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="template">
                <FileText aria-hidden /> Template
              </TabsTrigger>
              <TabsTrigger value="email">
                <Mail aria-hidden /> Raw email
              </TabsTrigger>
            </TabsList>
            <TabsContent value="template" className="mt-3">
              <fieldset className="grid gap-2">
                <legend className="sr-only">Request template</legend>
                {templates.isLoading &&
                  Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-16 rounded-lg" />)}
                {templates.data?.map((t) => (
                  <label
                    key={t.code}
                    className={cn(
                      "flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors hover:bg-accent/40",
                      template === t.code && "border-cobalt bg-accent/50",
                    )}
                  >
                    <input
                      type="radio"
                      name="template"
                      value={t.code}
                      checked={template === t.code}
                      onChange={() => setTemplate(t.code)}
                      className="mt-1 accent-[var(--cobalt)]"
                      data-testid={`template-${t.code}`}
                    />
                    <span>
                      <span className="block text-sm font-medium">{t.title}</span>
                      <span className="block text-xs text-muted-foreground">{t.description}</span>
                    </span>
                  </label>
                ))}
              </fieldset>
            </TabsContent>
            <TabsContent value="email" className="mt-3 grid gap-2">
              <Label htmlFor="raw-email">Email from the site manager</Label>
              <Textarea
                id="raw-email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                rows={9}
                placeholder={"Objet : Besoin de 2 caristes\n\nBonjour, nous avons besoin de 2 caristes CACES R489 cat. 3…"}
              />
              <p className="text-xs text-muted-foreground">Names, emails and phone numbers are masked before any AI call.</p>
            </TabsContent>
          </Tabs>
          <DialogFooter className="mt-5">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={!canSubmit || create.isPending} data-testid="create-mission">
              {create.isPending && <Loader2 className="animate-spin" aria-hidden />}
              Create mission
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

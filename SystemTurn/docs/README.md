# SystemTurn Docs

These docs capture **what SystemTurn is today** and **what is still missing**, as the starting point for Spec-Driven Development (SDD).

## Reading order

| # | Doc | Read it to answer |
|---|-----|-------------------|
| 1 | This page | What is SDD and why are we writing docs first? |
| 2 | [01-current-state.md](01-current-state.md) | What does the system actually do right now? |
| 3 | [02-gaps-and-questions.md](02-gaps-and-questions.md) | Where does the code differ from the plan, and what must we decide? |
| 4 | [infra-onboarding.md](infra-onboarding.md) | How do I bring up the AWS state bucket, budget and env roots? |

## The product in one paragraph

SystemTurn is a **virtual queue** (fila virtual). A **client** (cliente) taps a button on their phone and gets a ticket like `T-011`. An **advisor** (asesor) sees the queue and presses "call next", which serves the oldest pending ticket. Everyone can see live numbers: how many people are waiting, how many were served, and the average wait.

## Team

| Person | Area |
|--------|------|
| David, Daniela | Frontend (React) |
| Kevin | Data |
| Edgar | Backend (Spring Boot) |
| Diover | Testing |

## SDD in five minutes

**The problem SDD solves.** Without a written agreement, each person builds from their own memory of a whiteboard meeting. Frontend assumes one response shape, backend returns another, and testing doesn't know what "correct" means. Bugs then are not code mistakes, they are *disagreements* discovered late.

**The idea.** Write down *what* the system must do before writing *how*. The spec becomes the single source of truth that code, tests, and reviews are checked against.

**The cycle**

```text
explore → propose → spec → design → tasks → apply → verify → archive
```

| Phase | Question it answers | Output |
|-------|--------------------|--------|
| Explore | What exists? What are the options? | Notes (these docs are this phase) |
| Propose | What change do we want and why? | Short proposal: intent, scope, out of scope |
| Spec | What must be true when we are done? | Requirements + scenarios (Given/When/Then) |
| Design | How will we build it? | Technical decisions and tradeoffs |
| Tasks | In what order? | A checklist of small, testable steps |
| Apply | — | Code + tests |
| Verify | Does the code match the spec? | Pass/fail per scenario |
| Archive | — | Spec merged into the permanent "living" spec |

**A requirement vs. a scenario**

- *Requirement*: "The system MUST serve pending tickets in order of request time."
- *Scenario* (testable example):
  - **Given** ticket 7 requested at 08:34 and ticket 9 at 08:41, both pending
  - **When** the advisor calls next
  - **Then** ticket 7 becomes Attended, and ticket 9 is now first in line

If you can't write a scenario for a requirement, the requirement is still too vague.

## Why docs first

You can't spec a change against a baseline you don't know. The code already makes many decisions (FIFO by time, `max + 1` numbering, in-memory storage). Some match the whiteboard, some don't. Writing them down turns hidden decisions into visible ones the team can confirm or change.

## Next step

Read [01-current-state.md](01-current-state.md), then bring the open questions in [02-gaps-and-questions.md](02-gaps-and-questions.md) to the team. Each answered question becomes input for the first SDD proposal.

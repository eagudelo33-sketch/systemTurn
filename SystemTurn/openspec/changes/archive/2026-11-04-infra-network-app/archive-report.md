# Archive Report: infra-network-app

**Closed**: 2026-11-04 · **Verdict**: PASS WITH WARNINGS · **Evidence revision**: `sha256:e1f1fff33eeb55123edc69fe62ff1890713cde168620e13f36a0415c2694b128`

## Outcome

Reusable Terraform modules `network` (VPC 2 AZs x 3 tiers, `nat_mode`, isolated data tier, endpoints) and `app` (ECR, RDS PostgreSQL 17, ECS Fargate, ALB, S3 + CloudFront, IAM), wired into `envs/dev`. The paid layer is behind `enable_app` (default `false`), so the default dev apply costs about $0. `prod` is unchanged. No application or API change.

| Evidence (no AWS credentials) | Result |
|-------------------------------|--------|
| Requirements / scenarios touched | 16 / 26, all compliant |
| `terraform test` | network 11 · app 11 · dev 7 · prod 6 · bootstrap 16 |
| fmt, validate, tflint, trivy | clean (inline trivy ignores AWS-0053/0054/0011/0104 with reasons) |
| `check-conventions.sh` / self-test | 25/25 · 15/15 |
| Lock files | unchanged by test runs; module locks gitignored |

## Main specs

| Spec | Action | Req | Scenarios |
|------|--------|-----|-----------|
| `infra-network` | Created from the delta (header normalized to `# Infra Network Specification`, `## Purpose`, `## Requirements`) | 5 | 11 |
| `infra-app` | Created the same way | 8 | 11 |
| `infra-environments` | Merged: scope text, "Independent env roots" (`env_roots_declare_no_resources` now prod only), new "Dev composes network and app modules", operational notes | 6 | 13 |
| `infra-cost-guardrails` | Merged: scope text, new "Dev paid layer is a switch, off by default", operational note | 5 | 10 |

Specs index total: 63 requirements, 135 scenarios.

## Docs

- New `docs/infra.md`: what exists, cost, how to turn the app on and off, why a switch.
- `docs/infra-onboarding.md`: section 8 (prod still not wired) and 8b corrected; still 97 lines (limit 100).
- `docs/README.md`: feature map, status, roadmap (items 4-5 merged as `infra-network-app`), decisions, gaps, obligations.

## Delivery

Not committed. ~1,905 changed infra lines (above the 800 budget); `tasks.md` suggests a two-PR split. At verification time the checked-out branch was `feat/e2e-browser-tests`; move the work to `feat/infra-network-app` before committing.

## Deviations

- `infra-network` and `infra-app` delivered as one change instead of two roadmap items.
- `prod` not wired (was planned with `infra-network`).
- Modules pin `aws >= 6.69` (minimum), not `~> 6.69`; roots keep the exact pins and lock files.

## Open obligations

**Next infra change** (`infra-cicd` or a prod wiring change)
1. Prove the region and `default_tags` on a provider-backed resource in plan (still output-only).
2. Bump the tflint aws plugin to 0.50.0 on/after 2026-11-06.
3. Add run blocks for `/api/*` behavior, SPA rewrite function and ALB prefix-list ingress.
4. Wire `prod`: `nat_mode = "per_az"`, deletion protection, final snapshot, longer backups.
5. Keep `db_pool_size` x tasks < RDS `max_connections` when scaling (documented, not enforced).
6. Before a public deploy: ACM/HTTPS on the ALB, WAF, CloudFront-to-ALB secret header.

**live-updates**: add a WebSocket path (`/ws/*` behavior); ALB idle timeout is already 300 s.

**infra-cicd** (carried from infra-foundation, unchanged): image build and push to ECR, `terraform test` per root and module (fail on "0 passed"), tflint, trivy, `check-conventions.sh`.

## Operational (user-run, unverified by design)

First real apply of the dev network; then with `enable_app = true`: task start through NAT, ALB health check passing, secret injection, CloudFront routing of `/api/*` and SPA routes; confirm ~$0 in Cost Explorer after turning the app off.

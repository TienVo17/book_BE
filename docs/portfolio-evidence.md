# Portfolio Evidence

What this demo actually does, how it was verified, and where the limits are.
Every number below came from a command run on the date shown — nothing here is
estimated.

**Verified on:** 2026-08-03
**Environment:** Windows 11, Docker Engine 29.5.3, Java 17 (Temurin), Node 18,
Testcontainers pinned to Docker client API `1.44`, `agent-browser` 0.31.1
(bundled Chromium).

## Scope and honesty boundary

- Test data only. No real customer data, no real payment, no PII.
- VNPay is **sandbox contract only**: order creation and payment-URL generation
  are exercised, and a live callback has **not** been demonstrated because
  sandbox credentials are not available in this environment.
- No SLA, no production-security claim, no field Core Web Vitals.
- Free-tier hosting (Render/Vercel/Aiven) is a convenience demo and can cold
  start; the Docker local stack is the deterministic fallback used for evidence.

## Code state

Both repositories are separate Git repositories, reviewed and tested
independently.

| Repo | Branch | Baseline commit | Working tree |
|---|---|---|---|
| `book_BE` | `fix/flyway-auto-repair` | `7ce5689bc19eefebeb1ebbe45d7e81bbf4b5d1fc` | 63 files changed, uncommitted |
| `book_FE` | `fix/vercel-api-base-url` | `fb6441c9da871296dd0b8bf8d3acaadd86e5b18b` | 80 files changed, uncommitted |

The commits above are the **baseline before** this work. The results in this
document describe the current uncommitted working tree; the SHAs will change
once the work is committed, and this table must be updated at that point.

## Verification results

| Gate | Command | Result |
|---|---|---|
| Backend unit | `mvnw -B -Dapi.version=1.44 verify` | PASS — 35/35 (Surefire) |
| Backend integration | same run (MySQL Testcontainers) | PASS — 134/134 (Failsafe) |
| Frontend suite | `npm test -- --watchAll=false --runInBand` | PASS — 25 suites, 180 tests |
| Frontend typecheck | `npx tsc --noEmit` | PASS |
| Frontend build | `npm run build` | PASS (rewrites the robots `Sitemap:` URL) |
| Browser rehearsal | `scripts/rehearsal-run-scenarios.sh` ×10 | PASS — 60/60 rows |

Integration tests run against real MySQL via Testcontainers, not H2, because the
Flyway migrations and the atomic stock UPDATEs are MySQL-specific.

## Scenarios covered

Six browser scenarios, each executed 10 consecutive times against a freshly
reset fixture set. Structured rows (`run`, `timestamp`, `scenario`, `status`,
`elapsed_ms`, `traceId`, `note`) are in
[`../../plans/260803-0841-proof-first-ecommerce-portfolio/reports/browser-rehearsal-evidence-260803.tsv`](../../plans/260803-0841-proof-first-ecommerce-portfolio/reports/browser-rehearsal-evidence-260803.tsv).

| # | Scenario | What it proves | Worst case |
|---|---|---|---|
| 1 | Discovery | Catalog renders, 0 console errors | 2.1 s |
| 2 | Cart / two-tab | A second tab's write is reconciled; no line is silently lost | 4.4 s |
| 3 | Address + COD | Coupon applied, order committed, stock decremented exactly once, cart cleared only after the committed response | 0.6 s |
| 4 | VNPay | Order + payment-URL contract (contract-only, see boundary above) | 0.3 s |
| 5 | Admin | Atomic stock delta, order state advance, and the same call denied `403` for a customer token | 0.7 s |
| 6 | Failure / recovery | Traced `401`, response-loss retry produces exactly one order, forced stock conflict changes nothing | 1.0 s |

Two further scenarios are covered by integration tests rather than the browser
runner: checkout idempotency edge cases (`CheckoutIdempotencyIT`, 9 tests) and
the VNPay-callback-versus-cancel race (`VNPayCancelRaceIT`).

## Invariants worth pointing at

- **Checkout is idempotent.** The same user plus the same `Idempotency-Key`
  yields one order, one stock decrement and one coupon increment, whether the
  requests arrive sequentially or concurrently. A response-loss retry replays a
  byte-identical DTO; the replay reads immutable snapshots, so a later coupon
  rename, coupon deletion or payment-state change cannot alter it. The same key
  with a materially different request returns `409` and mutates nothing.
- **Stock never goes negative or overflows.** Checkout, cancellation and admin
  delta all use conditional `UPDATE`s, never read-then-write.
- **Payment and cancellation cannot both win.** Optimistic locking means exactly
  one commits; the loser gets `409`. A canceled-and-restored order can never
  later be marked paid.
- **Object-level authorization.** User A cannot read or mutate User B's address,
  order, cart or wishlist. Verified by `OwnershipAuthorizationIT` (8 tests).
- **Admin surface is `ADMIN`-only.** 70 assertions across `/api/admin/**` cover
  anonymous, `USER` and `STAFF`-only tokens.
- **Errors are traceable.** Every controller-owned 4xx/5xx and every security
  denial returns `{timestamp,status,code,message,path,traceId}`. `X-Trace-Id` is
  returned in the response header, matches the body, and is exposed via CORS so
  the browser can read it. During this work a live `500` was diagnosed by
  grepping the logs for one browser-visible trace ID.

## SEO and public metadata

Verified in a browser against the isolated stack on 2026-08-03:

| Check | Result |
|---|---|
| Canonical slug URL (`/sach/dac-nhan-tam`) | Loads the correct product; `title`, `canonical`, `og:*` and JSON-LD all match the visible content |
| Legacy numeric URL (`/sach/1`) | Loads the same product and points its canonical at the slug |
| JSON-LD vs visible page | `name` equals the `h1`; `offers.price` equals the displayed price |
| noindex matrix (7 routes) | `/` indexable; `/gio-hang`, `/thanh-toan`, `/order`, `/profile`, `/dang-nhap` and an unknown route all `noindex,nofollow` |
| Advertised sitemap URL | `robots.txt` pointed at the backend origin for that environment; the URL returned `application/xml`, not SPA HTML |

An all-digit slug is a real case — a title such as "1984" slugifies to `1984` —
so a purely numeric identifier is resolved by trying the book id first and
falling back to a slug lookup. Without that fallback the site's own canonical
URL would 404. Covered by `SachApi.identifier.test.ts`.

`robots.txt` and the app's noindex list are kept identical; the `Sitemap:` line
is rewritten at build time from `REACT_APP_API_BASE_URL`, and an invalid origin
fails the build rather than publishing an unreachable URL.

## Accessibility

21 behavior tests across 5 suites cover accessible names, programmatic labels,
live regions, busy state and keyboard operation on the cart, checkout, order
history and admin order pages.

Verified in a real browser: per-product control names, labelled radiogroups for
address and payment, a labelled coupon field, inline alerts carrying the support
trace ID, no horizontal overflow at 320 px, and no overflow at 200 % zoom.

### Lighthouse (lab)

Measured 2026-08-04 with Lighthouse 12.8.2, headless Chrome, against the warm
local stack. These are **lab** numbers under Lighthouse's default mobile
emulation (simulated 3G, 4× CPU slowdown) — not field Core Web Vitals.

| Page | Performance | Accessibility | Best Practices | SEO |
|---|---|---|---|---|
| Catalog (`/`) | 79 | **100** | 96 | **100** |
| Checkout (`/thanh-toan`) | 67 | **100** | 100 | 63 |

Raw summary: [`../../plans/260803-0841-proof-first-ecommerce-portfolio/reports/lighthouse-summary-260804.json`](../../plans/260803-0841-proof-first-ecommerce-portfolio/reports/lighthouse-summary-260804.json).

Accessibility ≥ 90 is met on both pages. Reading the other numbers honestly:

- **Performance is below the 80 target** (catalog 79, checkout 67). The cause is
  not application code: TBT is 0 ms and catalog CLS is 0. It is render-blocking
  third-party CSS — Bootstrap, Font Awesome and Google Fonts loaded from CDNs —
  which costs seconds under simulated 3G. Self-hosting and subsetting those
  assets is the fix; that is a build-pipeline change beyond this scope, so the
  number is reported as-is rather than tuned for the score.
- **Checkout SEO 63 is expected and correct.** `/thanh-toan` is deliberately
  `noindex,nofollow`, and Lighthouse penalises exactly that. A high SEO score
  here would mean the privacy rule was broken.
- **Checkout CLS 0.367** comes from product images arriving after first paint in
  the cart list. Worth fixing with explicit image dimensions; recorded rather
  than hidden.

Measuring this found and fixed four real defects: a duplicate Font Awesome
stylesheet loaded twice, render-blocking Bootstrap JS, 45 colour-contrast
violations from four palette tokens, and cart quantity buttons squeezed to
22.6 px (below the WCAG 2.2 24 px minimum). Catalog Performance improved 59 → 79
and both pages went to Accessibility 100.

## Reproducing locally

```bash
# 1. Backend + MySQL + frontend
cd book_BE
export JWT_SECRET='<base64-signing-key>'   # runtime only; never commit
docker compose up --build -d

# 2. Check it is alive
curl http://localhost:8080/health          # {"status":"UP"}
curl 'http://localhost:8080/api/sach?page=0'

# 3. Frontend at http://localhost:3000
```

Flyway creates the schema, seeds reference data and a default admin on first
start. Fresh startup was measured three times: `281 s` for the first run
(image build) and `37 s` / `36 s` afterwards.

### Browser rehearsal (isolated stack)

The rehearsal stack shares no container, database, volume or port with the dev
stack, so it can be reset destructively without touching development data.

```bash
cd book_BE
export JWT_SECRET='<base64-signing-key>'
docker compose -f docker-compose.rehearsal.yml -p rehearsal up --build -d
./scripts/rehearsal-run-scenarios.sh 1     # one run, six evidence rows
```

`scripts/rehearsal-fixture-reset.sh` refuses to run unless it finds the exact
rehearsal container **and** database, and explicitly refuses the development
database even if pointed at it. Both refusals were exercised. It arms its
cleanup trap before the first mutation and removes only the IDs it created; after
10 runs the database held zero leftover rehearsal rows.

## Known limitations

- **Bearer token in `localStorage`.** Readable by any script on the page.
  Acceptable for a demo; a real go-live needs HttpOnly cookies plus CSRF
  defence. The XSS surface was reduced (no `dangerouslySetInnerHTML`, encoded
  email HTML), but that is mitigation, not a fix.
- **SPA returns HTTP 200 for unknown routes.** The client renders a `NotFound`
  screen and marks it `noindex`, but the origin's history fallback still answers
  200. This is not an origin-level 404.
- **Server cart API exists but is unused.** The frontend cart is local-only by
  design; server-cart sync/merge is deliberately out of scope.
- **`STAFF` grants no admin access.** The claim remains in the JWT payload for
  compatibility only.
- **Insufficient stock returns `400`, not `409`.** Semantically this is a state
  conflict and `409` would be more correct, matching the cancel and idempotency
  conflicts. It is locked by baseline tests and is a public contract change, so
  it is recorded here rather than changed silently.
- **Email delivery is best-effort after configuration.** `MAIL_FROM` missing is
  caught up front (registration returns a retryable `503` and rolls back rather
  than creating an unactivatable account), but a later SMTP outage during
  password reset is not surfaced to the caller, by design, to avoid account
  enumeration.
- **Lighthouse Performance is below target** (catalog 79, checkout 67) because
  of render-blocking CDN CSS, and checkout CLS is 0.367 from unsized images. See
  the Accessibility section for the full numbers and reasoning.

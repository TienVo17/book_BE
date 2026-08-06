# Portfolio Evidence

What this demo actually does, how it was verified, and where the limits are.
Every number below came from a command run on the date shown — nothing here is
estimated.

**Verified on:** 2026-08-06 (review work); earlier sections carry their own dates
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

| Repo | Branch | Baseline commit | This work |
|---|---|---|---|
| `book_BE` | `feat/danh-gia-nen-tang` | `6225aee` | 7 commits, one per phase |
| `book_FE` | `feat/danh-gia-nen-tang` | `86d6cc6` | 7 commits, one per phase |

One commit per phase in each repository, in the order the phases ran. The two
repositories must be reviewed and released together: the public review payload,
the moderation state name and the review-image endpoints are a shared contract,
so shipping either side alone breaks the other. Earlier work on the same pair of
repositories (error schema, API base URL) shipped the same way.

Verified against the committed state, not just a working tree.

## Verification results

Last full run 2026-08-06.

| Gate | Command | Result |
|---|---|---|
| Backend unit | `mvnw -B -Dapi.version=1.44 verify` | PASS — 84/84 (Surefire) |
| Backend integration | same run (MySQL Testcontainers) | PASS — 220/220 (Failsafe), 7 min 27 s |
| Frontend suite | `npm test -- --watchAll=false --runInBand` | PASS — 34 suites, 231 tests |
| Frontend typecheck | `npx tsc --noEmit` | PASS |
| Frontend build | `npm run build` | PASS (rewrites the robots `Sitemap:` URL) |
| Browser rehearsal | `scripts/rehearsal-run-scenarios.sh` ×10 | PASS — 80/80 rows (see the Phase 7 section) |

Integration tests run against real MySQL via Testcontainers, not H2, because the
Flyway migrations and the atomic stock UPDATEs are MySQL-specific.

## Scenarios covered

Seven browser/API scenarios plus a cleanup row, each executed 10 consecutive
times against a freshly reset fixture set — 8 rows per run, 80 rows total.
Structured rows (`run`, `timestamp`, `scenario`, `status`, `elapsed_ms`,
`traceId`, `note`) are in
[`../../plans/260805-1039-danh-gia-san-pham-chuan-ecommerce/reports/browser-rehearsal-evidence-260806.tsv`](../../plans/260805-1039-danh-gia-san-pham-chuan-ecommerce/reports/browser-rehearsal-evidence-260806.tsv).

Worst case is the slowest of the 10 runs on 2026-08-06.

| # | Scenario | What it proves | Worst case |
|---|---|---|---|
| 1 | Discovery | Catalog renders, 0 console errors | 2.05 s |
| 2 | Cart / two-tab | A second tab's write is reconciled; no line is silently lost | 4.06 s |
| 3 | Address + COD | Coupon applied, order committed, stock decremented exactly once, cart cleared only after the committed response | 0.51 s |
| 4 | VNPay | Order + payment-URL contract (contract-only, see boundary above) | 0.34 s |
| 5 | Admin | Atomic stock delta, order state advance, and the same call denied `403` for a customer token | 0.57 s |
| 6 | Failure / recovery | Traced `401`, response-loss retry produces exactly one order, forced stock conflict changes nothing | 0.93 s |
| 7 | Review helpful vote | A delivered order makes the buyer eligible; a vote toggles off on the second press, the author is denied `403` on their own review, and deleting the review takes its votes with it | 1.27 s |
| — | Cleanup | The closing fixture reset verified residue = 0 and exited 0 | — |

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
./scripts/rehearsal-allow-frontend-csp.sh  # see the CSP note in the Phase 7 section
./scripts/rehearsal-run-scenarios.sh 1     # one run, eight evidence rows
```

The runner exits non-zero if any row says `FAIL`, so `for i in $(seq 1 10); do
./scripts/rehearsal-run-scenarios.sh "$i" || echo "run $i failed"; done` is a
real gate rather than a transcript.

`scripts/rehearsal-fixture-reset.sh` refuses to run unless it finds the exact
rehearsal container **and** database, and explicitly refuses the development
database even if pointed at it. Both refusals were exercised. It arms its
cleanup trap before the first mutation and removes only the IDs it created; after
10 runs the database held zero leftover rehearsal rows.

## Review images — Phase 6 verification (2026-08-06)

Customer reviews can carry up to five JPEG, PNG or WebP images, each limited to
5 MB. Review assets use the separate Cloudinary folder `web-ban-sach/reviews`;
the backend validates signatures from file bytes rather than trusting the
client-provided media type. Upload is authenticated, rate-limited to 20 attempts
per 10 minutes per user, and capped by a lifetime quota of 50 accepted images.
Only the review owner or an ADMIN can remove an attached image.

| Gate | Result |
|---|---|
| Backend full verification | PASS — 81 Surefire + 219 Failsafe tests (MySQL Testcontainers) |
| Backend focused image/admin/error/MVC tests | PASS; lock-race suite 10/10 and V14 migration suite 2/2 |
| Frontend full suite | PASS — 33 suites, 227 tests |
| Frontend typecheck | PASS — `tsc --noEmit` |
| Frontend production build | PASS |
| Real Cloudinary upload/delete | **Not verified** — no `CLOUDINARY_URL` was supplied to this environment |

Each upload requires an `Idempotency-Key`. Replaying the same review/key/file
returns the stored image without a second Cloudinary upload or lifetime-quota
increment; reusing the key for different bytes returns `409`. The public response
contains only the image ID and URL, never the Cloudinary public ID. Replays still
count toward the 20-per-10-minute rate limit.

The review service reads each accepted payload once, reuses those bytes for SHA-256,
magic-byte validation and Cloudinary upload, avoiding a second full-size byte array.
Upload and review deletion acquire the same pessimistic review-row lock, which
prevents a concurrent delete from committing between the Cloudinary upload and
its database row. A deterministic two-worker MySQL integration test confirms the
delete reaches that lock and waits until upload commits. A database failure after
upload triggers deletion of the new asset. Deletion calls Cloudinary before
deleting the database row; if Cloudinary fails, the row and public ID remain
available for a manual retry.

This is not an atomic transaction across MySQL and Cloudinary. There is no
durable outbox or background retry worker. If both a database insert and the
immediate compensating Cloudinary delete fail, the new asset can remain without a
durable retry record. During a multi-image deletion, an external failure after
one asset has already been deleted can leave retained DB rows temporarily
inconsistent with Cloudinary until an operator retries or reconciles them.
`RateLimiter` is per JVM; 20 uploads/10 minutes is accurate for the current
single-instance Render topology, but a multi-replica deployment needs a shared
DB/Redis counter. Signature checks are a format filter, not malware scanning.
`CLOUDINARY_URL` is mandatory; there is no local-storage fallback.

The ADMIN catalog-image upload now benefits from the same byte-signature checks
inside `CloudinaryService`; it does not rely solely on a browser `Content-Type`.

## Reviews — Phase 7 verification (2026-08-06)

Reviews are now restricted to buyers who actually received the goods, the star
average is derived from visible reviews on every write path, and moderation
survives a delete-and-repost attempt.

### Schema cleanup and a startup defect it exposed

`V15` raises `danhgia.ma_don_hang` to `NOT NULL`, adds the foreign key to
`don_hang`, and drops the legacy `is_active` column. It refuses to touch the
schema unless `SELECT COUNT(*) FROM danhgia WHERE ma_don_hang IS NULL` is 0 —
the guard runs before the first `ALTER` and raises `SQLSTATE 45000` otherwise.
`V15ReviewSchemaCleanupTest` proves the gate, the post-conditions, and that
re-running the file changes neither schema nor data.

Standing the isolated rehearsal stack up on the migrated schema found a defect
no integration test had caught: `V14` created `danhgia_hinh_anh.noi_dung_sha256`
as `CHAR(64)` while the entity maps `VARCHAR(64)`, so with the real
`ddl-auto=validate` setting Hibernate refused to build the
`entityManagerFactory` and **the application did not boot**. `V16` converts the
column; `V16ReviewImageHashTypeTest` locks the type, and the rehearsal backend
now boots and answers `{"status":"UP"}` at Flyway version 16.

| Gate | Command | Result |
|---|---|---|
| Review domain free of the legacy flag | `grep -rn "isActive" book_BE/src book_FE/src` | PASS — remaining hits are the book/coupon `isActive` fields and one regression assertion that the public review DTO must **not** contain it |
| Order-link data gate | `SELECT COUNT(*) FROM danhgia WHERE ma_don_hang IS NULL` | 0 on the fully migrated stack |
| Migrated schema | `SELECT version … ORDER BY installed_rank DESC LIMIT 1` | `16`; `noi_dung_sha256` is `varchar`; `danhgia.is_active` is gone |
| Backend boot under `ddl-auto=validate` | `GET /health` on the rehearsal stack | `{"status":"UP"}` |

`ReviewInvariantIT` runs one mixed sequence — post, edit, vote, attach image,
admin hide, admin show, unvote, delete image, delete review, repost — and
asserts the aggregate, public exposure and moderation invariants after *each*
step. It is the net for interactions that per-phase tests miss: helpful votes
and image attachments must not move the rating, hiding must remove the review
from the list, the total and the average at once, and the tombstone must still
block the repost at the end of the whole chain.

### Admin dashboard, and the demo orders it must ignore

Phase 2 seeded delivered orders so legacy reviews have proof of purchase. Those
orders carry `la_don_demo = 1` and every dashboard aggregate excludes them.
Measured against the rehearsal stack on 2026-08-06:

| Number | API `GET /api/admin/thong-ke` | Direct SQL |
|---|---|---|
| Orders | `totalOrders` = 3 | 11 rows total, 8 of them demo → 3 real |
| Revenue | `totalRevenue` = 356 000 | 356 000 excluding demo |
| Top sellers | 4 titles, 5 units | 8 demo order lines excluded from the `GROUP BY` |
| Reviews resting on demo orders | — | 8 |

Honest reading: the **count** difference (11 → 3) demonstrates the exclusion
directly. The **revenue** figure is identical either way, because the seeded
demo orders are unpaid (`trang_thai_thanh_toan = 0`) and would not have been
counted regardless. The revenue exclusion is enforced by the query
(`DonHangRepository.sumDoanhThu`) and covered by `ThongKeSeedExclusionIT`, not
by this measurement.

Reading those numbers in the browser required a fix: the dashboard model
declared `tongDoanhThu` / `topSachBanChay` while the API returns
`totalRevenue` / `topBanChay`, and `authRequest<T>` only casts. Every tile
rendered `0đ` with an empty best-seller table while the API had data.
`AdminApi.getThongKe` now maps the fields explicitly and is covered by
`AdminApi.thongke.test.ts`. The "reviews awaiting moderation" banner was
removed rather than wired up: there is no such queue — moderation states are
`HIEN_THI` and `DA_AN` only — and the field it read never existed.

### Accessibility and Lighthouse

Measured in Chrome against the rehearsal stack on 2026-08-06, on a product page
including the review block, and again while signed in as a buyer with a
delivered order so the review form and its image picker were present:

| Width | Signed in with a delivered order | Review form + image picker rendered | Horizontal overflow |
|---|---|---|---|
| 320 CSS px | no | no (review list only) | none (`scrollWidth` == `clientWidth`) |
| 640 CSS px (200 % zoom equivalent) | no | no (review list only) | none |
| 1280 CSS px | no | no (review list only) | none |
| 320 CSS px | yes | yes | none |
| 640 CSS px (200 % zoom equivalent) | yes | yes | none |

55 focusable controls on the anonymous product page, none with a negative
`tabindex`.

Lighthouse 12.8.2, headless Chrome, default mobile emulation, product page
`/sach/1`:

| Run | Performance | Accessibility | Best Practices | SEO | CLS | LCP |
|---|---|---|---|---|---|---|
| Before | 50 | **100** | 96 | **100** | 0.881 | 8.0 s |
| After sizing the product image | 63 | **100** | 96 | **100** | **0** | 9.1 s |

The product-image carousel replaced a short loading heading with a 250 px image
frame and rendered the image without dimensions, so the whole page below it
jumped when the image arrived. Reserving the frame and passing explicit
`width`/`height` took CLS to 0. LCP stays around 9 s for the reason already
recorded for the catalog page: render-blocking third-party CSS from CDNs, which
is a build-pipeline change outside this scope. It is reported, not tuned.

### Rehearsal evidence is now falsifiable

Three defects made the previous "10 independent runs" claim unprovable, and all
three are fixed:

- `rehearsal-fixture-reset.sh` sent every `mysql` invocation's stderr to
  `/dev/null`. Two cleanup statements named a table that does not exist
  (`sach_the_loai`; the real name is `sach_theloai`), so those deletes had never
  run and nobody could have known. `sql()` now reports failures, aborts during
  provisioning, and — after the cleanup pass — counts the residue itself and
  exits non-zero if anything is left or any statement failed.
- `rehearsal-run-scenarios.sh` exited 0 even when a row said `FAIL`. It now
  exits non-zero if any row fails, and the closing fixture reset's status is
  itself an evidence row.
- Scenario 7 assumed the order was already delivered, but the admin step
  advances delivery by one state and only reached `DANG_GIAO`. Every run had
  been failing the review POST with `403`. It now advances to `DA_GIAO` and
  asserts the state before posting.

The VNPay row also stopped accepting any body that merely contained a
4xx/5xx-shaped number. It now passes only on a `paymentUrl` or on the common
error envelope (`code` + `traceId`), and records which of the two it saw.

After the 10 runs: 80 PASS rows, 0 FAIL rows, and zero leftover rehearsal
books, users, coupons or orders.

### Full suite after the Phase 7 changes

| Gate | Result |
|---|---|
| Backend `mvnw -B verify` | PASS — 84 Surefire + 220 Failsafe, 7 min 27 s |
| Frontend suite | PASS — 34 suites, 231 tests |
| Frontend typecheck / build | PASS |

The first full run failed, and the failure was informative:
`V11ReviewSchemaIdempotencyTest` migrated to the newest version and then replayed
`V11`, which reads `danhgia.is_active` — a column `V15` had just dropped. The
test is now pinned to version 11, the only state in which replaying `V11` is a
real recovery step. That is a behaviour change worth naming rather than a test
tweak: see the note in Known limitations.

### Limits of this evidence

- The browser scenarios ran with **one** CSP directive relaxed. The production
  `connect-src` allows only `http://localhost:8080` and the Render origin, so
  the rehearsal backend on `:8081` was blocked in the browser while `curl`
  succeeded — CSP is not applied by curl, which is exactly why API-level checks
  never surfaced it. `scripts/rehearsal-allow-frontend-csp.sh` patches the
  running rehearsal container only; `nginx.conf` is untouched, and every other
  directive was in force.
- Real Cloudinary upload and delete are still **not verified**: no
  `CLOUDINARY_URL` was supplied to this environment. The image tests mock
  `CloudinaryService`, which proves the application's contract with the client
  and its own database, not the storage provider's behaviour.

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
- **Lighthouse Performance is below target** (catalog 79, checkout 67, product
  63) because of render-blocking CDN CSS. Checkout CLS is still 0.367 from
  unsized images in the cart list; the product page was fixed to CLS 0 on
  2026-08-06 and the cart list has not had the same treatment. Catalog and
  checkout were last measured 2026-08-04 and have not been re-run since.
- **The frontend CSP hardcodes backend origins.** `connect-src` lists
  `http://localhost:8080` and one Render URL, so deploying the API anywhere else
  breaks every browser request while server-side checks keep passing. This cost
  a debugging cycle when the rehearsal backend moved to `:8081`. Making the
  allowed origin follow `REACT_APP_API_BASE_URL` at build time is the fix; it
  touches deployment config outside this scope and is recorded, not done.
- **Re-running `V11` is only possible before `V15`.** `V11` reads
  `danhgia.is_active` to derive `trang_thai`, and `V15` drops that column. The
  replay-safety test pins itself to version 11 for that reason. An operator
  recovering from a half-applied `V11` after `V15` has run must use the rollback
  runbook, not a re-run.

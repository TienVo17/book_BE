# Walkthrough Script (6–7 minutes)

A demo order for showing the project. Timings are targets, not measurements.
Run against the local Docker stack so nothing depends on free-tier cold starts.

**Before recording**
- `docker compose up --build -d`, then confirm `GET /health` returns `{"status":"UP"}`.
- Clear browser `localStorage` so the cart starts empty.
- Use test accounts only. Never show a real address, phone number or payment detail.
- Keep DevTools closed except where the script calls for it.
- Section 7 needs an account with a **delivered** order: place one, then advance
  it twice from the admin screen (`CHO_XU_LY` → `DANG_GIAO` → `DA_GIAO`). One
  advance is not enough and the review form will stay hidden.

Budget: 30 s framing, ~6 min of the eight sections, 30 s close.

---

## 0. Framing (~30 s)

State the boundary up front, before any feature:

> "Bookstore demo on test data. No real payments and no real customer data. What
> I want to show is not the feature list — it's what happens when things go
> wrong: duplicate submits, two tabs, expired sessions, races on stock."

## 1. Discovery → cart (~60 s)

- Catalog, open a product, add to cart.
- Point out the cart badge updating.

> "The cart is deliberately local. There's a server cart API, but the frontend
> doesn't use it — one writer module owns `localStorage`, so there's a single
> place where cart state can change."

## 2. Two tabs (~45 s)

- Duplicate the tab, change the cart in tab B, return to tab A and try to order.
- Show the warning and the refreshed lines.

> "Tab A's view was stale. Rather than silently submitting the old cart, it
> re-reads storage, compares a fingerprint and asks the user to review. A
> checkout can't be built from a cart the user never saw."

## 3. Checkout with COD (~60 s)

- Choose the address, apply a coupon, place a COD order.
- Show the order in history, and stock decremented by one.

> "The coupon and total are recalculated server-side — the client's numbers are
> never trusted. The cart and the pending idempotency key are cleared only after
> the server confirms the order committed."

## 4. The interesting part: retry (~75 s)

This is the core of the demo — do not rush it.

- Open DevTools → Network, throttle to offline, click "Đặt hàng".
- Show the inline error carrying a trace ID.
- Go back online, click again with the same key.
- Show one order in history and stock decremented once.

> "The first request reached the server; the response didn't come back. The
> retry sends the same idempotency key, so instead of a second order the server
> replays the first response. One order, one stock decrement — and the replay
> reads stored snapshots, so it stays identical even if a coupon is renamed
> afterwards."

## 5. Failure and recovery (~60 s)

- Show a forced stock conflict: order more than exists → error, stock unchanged.
- Cancel an order → stock restored, coupon usage returned.
- Try to cancel it again → `409`, no double restore.

> "Cancellation flips state under an optimistic lock before touching stock, so a
> double-cancel can't refund inventory twice."

## 6. Admin (~60 s)

- Sign in as `ADMIN`, adjust stock by a signed delta, advance an order state.
- Attempt the same admin call with a customer token → `403`.

> "The frontend guard is UX only. The backend authorizes every request
> independently — that `403` comes from the server, not the router."

## 7. Reviews: only real buyers, and moderation that sticks (~60 s)

Use the customer whose order was just advanced to delivered.

- On a product the account has **not** received, show the review form is absent
  and the reason the API gives (`CHUA_MUA` / `CHUA_NHAN_HANG`).
- On the delivered product, post a rating and a comment; show the star average
  and the count change on the product page.
- Attach an image, then remove it.
- As `ADMIN`, hide the review. Reload the product page signed out: it is gone
  from the list, from the average and from the total.
- Back as the author, delete the hidden review and try to post again.

> "Two things are worth watching here. The average isn't a seed value any more —
> it's recomputed from the visible reviews on every write path, including the
> admin hide. And hiding survives deletion: the author can delete their own
> post, but a tombstone on (user, book) means the moderated pair can't come back
> by deleting and reposting. The reviewer name is masked server-side, and no
> stable user id is in the public payload."

## 8. Traceability (~45 s)

- Trigger any error, copy the trace ID from the message.
- `docker compose logs backend | grep <traceId>` → the matching event.

> "Every error carries a trace ID the browser can read, and every failure logs
> one structured event. No tokens, no request bodies, no stack traces in the
> logs — just enough to find the request."

## 9. Close (~30 s)

> "Verified by the backend unit and integration suites against real MySQL, the
> frontend suite, and seven scenarios run ten times for eighty evidence rows —
> every row PASS, and the run exits non-zero if any row fails. The known
> limitations — the token in `localStorage`, VNPay being sandbox contract only,
> and Cloudinary never being exercised with real credentials — are written down
> in `portfolio-evidence.md` rather than left for someone to discover."

Exact counts move with every run; read them from `portfolio-evidence.md` on the
day you record rather than repeating a number from this script.

---

## Do not claim

- That it is production-ready, secure, or has an SLA.
- That VNPay works end to end — the live callback was never demonstrated.
- Any **field** Core Web Vitals. The Lighthouse numbers in
  `portfolio-evidence.md` are lab runs under mobile emulation, nothing more.
- That review images reach Cloudinary; no `CLOUDINARY_URL` was ever supplied,
  so that path is mocked in tests and unproven in production.
- That the free-tier deployment is reliable; it can cold start.

const BACKEND_ORIGIN = 'https://book-be-jakn.onrender.com';
const REQUEST_TIMEOUT_MS = 110_000;
const TARGETS = [
  { path: '/health', valid: body => body?.status === 'UP' },
  { path: '/api/the-loai', valid: body => Array.isArray(body) },
];

// Only public read endpoints are contacted. This worker has no HTTP entrypoint,
// credentials, database connection or access to users' browser sessions.
export default {
  async scheduled() {
    let failed = false;
    for (const target of TARGETS) {
      const startedAt = Date.now();
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
      let status = 0;
      let ok = false;
      try {
        const response = await fetch(`${BACKEND_ORIGIN}${target.path}`, {
          method: 'GET',
          redirect: 'error',
          cache: 'no-store',
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        });
        status = response.status;
        if (status === 200) {
          ok = target.valid(await response.json());
        } else {
          await response.body?.cancel();
        }
      } catch {
        // Report only status/timing. Never log response bodies or credentials.
      } finally {
        clearTimeout(timeout);
      }
      failed ||= !ok;
      console.log(JSON.stringify({
        event: 'backend_keep_alive', path: target.path, status, ok,
        elapsedMs: Date.now() - startedAt,
      }));
    }
    if (failed) throw new Error('Backend liveness or catalogue readiness check failed.');
  },
};

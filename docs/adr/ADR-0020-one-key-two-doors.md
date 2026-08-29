# ADR-0020 — One key, two doors; and a separate secret for MCP

> Status: Accepted
> Date: 2026-08-23

## Context

Until this batch, nothing was protected. `POST /disputes` triggered **billed** model calls,
`GET /disputes/{id}` returned a full audit trail, and the MCP server accepted any caller. That was
acceptable while nothing ran; it stops being acceptable the moment a `docker compose up` is
published.

The previous batch created a conflict the plan had not seen coming: **`audit.html` calls
`GET /disputes/{id}` from the browser**. An API key hard-coded in a statically served page is a
published secret.

And an asymmetry: the MCP server had never needed authentication, not out of negligence but **as a
property of the transport** — under STDIO the client *forked* the server, so nobody else could talk
to it. ADR-0016 put `get_transaction` and its neighbours on a socket.

## Decision

**One secret for the public surface, presented two ways.** Machines send `X-API-Key`; the page asks
for it on open and keeps it in `sessionStorage` — cleared when the tab closes, unlike
`localStorage`. One server-side check, therefore one thing to test.

**A second, DISTINCT secret for MCP** (`X-MCP-Secret`). Compromising the exposed surface must not
grant access to payment data: the two secrets do not protect the same thing and do not live at the
same place on the network.

**Spring Security on both modules**, not a hand-written servlet filter.

**Both services refuse to start when their secret is empty.**

Left open: `/actuator/health` (the compose healthcheck has no key, and a protected probe would leave
the container permanently `unhealthy`) and `/audit.html` (the page carries no data).

## Alternatives considered

- **Protect `POST`, leave `GET` open.** Zero lines of JavaScript. But the audit trail becomes public
  to anyone who knows a `disputeId` — and that trail is precisely the document this step makes
  readable. Indefensible on a payment system.
- **Basic auth for the browser plus a key for machines.** The native popup costs 0 lines of JS, but
  creates **two** authentication paths to configure, test and explain, for barely better ergonomics.
- **A bare servlet filter** (~40 lines, no dependency). Rejected: what we gain is not the filter —
  it is the ordered chain, the default security headers, and a single place to read what is open.
- **One secret for both surfaces.** One secret fewer to manage, one compromise more to suffer.
- **OAuth2 / JWT.** Deferred: an authorisation server does not demonstrate anything on `localhost`.

## Consequences

**`csrf` is disabled, and one has to be able to say why.** A CSRF attack exploits authentication the
browser attaches *automatically* to the request: a session cookie, a remembered `Authorization`. An
application header is never sent on its own — a third-party site cannot have it added. So there is
nothing to forge. Disabling CSRF without being able to say that is a mistake; saying it is the
answer.

**Startup fails rather than opening up**, and that is the most important property here. A service
that starts without authentication because a variable was missing is worse than an openly
unauthenticated one: it looks protected, the healthcheck is green, the demo works.

**The outbound MCP header costs twelve useful lines.** Spring AI exposes an
`ObjectProvider<McpClientCustomizer<Builder>>` in its transport autoconfiguration; the MCP SDK
extension point (`McpSyncHttpClientRequestCustomizer`) plugs in there and sees every outgoing
request. The fallback plan — redeclaring `List<NamedClientMcpTransport>` — was not needed; it would
have meant taking over connection parsing, the `JsonMapper` and the endpoint, because that bean does
**not** carry `@ConditionalOnMissingBean`.

**Two traps hit, both invisible on reading:**

1. **Spring Security filters ALL dispatch types, `ERROR` included.** A 404 triggers an internal
   dispatch to `/error`: the request re-enters the chain **without the original header**, becomes
   anonymous again, and the 401 **replaces** the 404. Symptom: `/actuator/env` returned 401 to an
   authenticated caller, while the logs said `Secured GET /actuator/env` right before. Hence
   `dispatcherTypeMatchers(ERROR).permitAll()` as the first rule, on both sides.
2. **The Boot 4 `@WebMvcTest` slice imports no security autoconfiguration.** `DisputeControllerTest`
   therefore stayed green asserting a `202` that production answers with `401` — a test green for
   the wrong reason. Fixed with `spring-boot-security-test` + `@Import(ApiSecurityConfig)` + the key
   on every request, **never** with `addFilters = false`: disabling the filters would have made the
   test pass by removing exactly what had just been added.

**Accepted debt, written down**: no rotation, no expiry, no revocation — meaningless without a
secret store. No rate limiting. No TLS: that is a load balancer's job, not the container's.

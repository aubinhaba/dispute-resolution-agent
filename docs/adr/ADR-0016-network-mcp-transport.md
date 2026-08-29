# ADR-0016 — The MCP server becomes a network service, not a child process

> Status: Accepted
> Date: 2026-08-21

## Context

Since step 2, `mcp-payment-server` has spoken **STDIO**: the MCP client launches it itself as a
subprocess (`java -jar …`) and talks to it over `stdin`/`stdout`. That was the right choice for
learning the protocol — no port, no network, no configuration.

The deployable step changes the success criterion: the system must **run and deploy**, which here
means fitting into containers orchestrated by `docker compose`. Three forces work against STDIO.

1. **A container does not fork a child process inside another container.** STDIO assumes the client
   shares a filesystem with the server and is allowed to fork a JVM. That is exactly what a
   container boundary removes.
2. **STDIO couples lifecycles.** The server is born and dies with its client, and only one client
   can consume it. It cannot be restarted on its own, scaled, or shared.
3. **STDIO imposes constraints that look like good practice.** With `stdout` carrying JSON-RPC, any
   stray byte corrupts the stream: banner off, console appender off, logs redirected to a file.
   Three settings one could mistake for hygiene, when they were only consequences of a transport.

## Decision

**The MCP server moves to Streamable HTTP** (`spring-ai-starter-mcp-server-webmvc`), exposed on
`/mcp`, and the client connects to it by URL
(`spring.ai.mcp.client.streamable-http.connections`).

The scope of the change, measured with `git diff --stat`: **`pom.xml` and `application.yml`. Zero
lines of Java.** `PaymentTools`, `@McpTool`, `@McpToolParam` and the four tool schemas are strictly
unchanged — verified by `McpToolDiscoveryIT`, in which no assertion moved.

This is the project's thesis about frameworks, made checkable: **the transport is a property of the
deployment, not of the tool contract.** A hand-rolled HTTP client would have put that boundary in
our code, and therefore on us (see ADR-0002, the same reasoning applied to the tool-calling loop).

## Alternatives considered

**Stay on STDIO and run both applications in a single container.** Technically possible: one
`Dockerfile` carrying both jars, the client forking the server. Rejected for two reasons. The
honest one: it demonstrates nothing about the network problem, which is the central notion of the
step. The structural one: a container running two application processes without a supervisor is a
known anti-pattern — no reliable health probe, no independent restart, and a `PID 1` that does not
reap its orphans.

**SSE rather than Streamable HTTP.** Spring AI exposes both
(`spring.ai.mcp.server.protocol` = `SSE` | `STREAMABLE` | `STATELESS`). SSE is MCP's *historical*
HTTP transport, superseded by Streamable HTTP in recent revisions of the specification: it keeps a
separate long-lived connection for responses, where Streamable HTTP answers on the request itself
and only switches to streaming when needed. Picking the deprecated transport for a new deliverable
would have been free debt.

## Consequences

**Positive.** Two independent images, separately restartable and separately probeable. The MCP
server gets its banner, its console logs and an `/actuator/health` back — the STDIO constraint
falls away. OAuth2 Resource Server on the MCP server becomes possible: it means nothing on a
transport without HTTP requests.

**Negative, and accepted.**

- **The client no longer guarantees its peer is available.** Under STDIO, if the client ran, the
  server ran. Now `dispute-agent` fails to start when `/mcp` is unreachable
  (`spring.ai.mcp.client.initialized: true`). `docker compose` carries the ordering in production
  and `McpServerProcess` carries it in tests — **that is the only real cost of the move, and it
  sits entirely in the test harness**: ~110 lines to start a peer and wait for its probe, where
  STDIO supplied one for free.
- **A network surface appears.** The server now listens on a port. Locally it is authenticated by a
  shared secret (ADR-0020); OAuth2 belongs to the AWS step. Writing that debt down here rather than
  discovering it later.

## Trap hit, not to be rediscovered

A `404` on `/mcp` with a server that had started and logged "4 tools registered".

Cause: `McpServerProperties` initialises the Java field `protocol = ServerProtocol.STREAMABLE`, but
the autoconfiguration condition carries
`@ConditionalOnProperty(name = "protocol", havingValue = "STREAMABLE", matchIfMissing = false)`,
while the SSE condition carries `matchIfMissing = true`. **Property absent ⇒ SSE wins**, in
contradiction with the field's own default.

The generalisation goes beyond MCP: *the default of a `@ConfigurationProperties` and the default of
a `@ConditionalOnProperty` are two different things.* Reading the former and inferring behaviour is
a mistake — only the latter decides whether the bean exists. Writing `protocol: STREAMABLE`
explicitly is therefore not redundant.

## Related

- ADR-0002 — the framework's tool-calling loop rather than a custom one: same thesis, another
  boundary.
- ADR-0008 — a standalone MCP server with no shared code: that autonomy is what makes this move
  free on the Java side.
- ADR-0015 — scope of the deployable step.

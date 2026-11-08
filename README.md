# docker-stack-manager

A local Spring Boot web UI to deploy, inspect, and manage Docker Swarm stacks without memorizing
shell commands or juggling terminal tabs.

## Why

When working on several microservices, each project typically has its own `docker-stack.yml` with
the exact deploy command commented at the top of the file. Switching between projects means
remembering that command, opening a terminal, tearing down the previous stack, and launching the
new one. **docker-stack-manager** removes all of that: it scans a root folder for every
`docker-stack.yml` it can find and gives each one a card in a web UI — deploy, remove, check
status, scale services, tail logs, and edit the file, all with a click.

## How it works

- **Scanner** — recursively walks a configurable root folder (`app.scan-root`) looking for
  `docker-stack.yml` files, up to `app.max-scan-depth` levels deep.
- **Parser** — reads the first lines of each file, finds the commented `docker stack deploy ...`
  command, and extracts both the exact command to run and the stack name — zero manual
  configuration per project. It also detects `source .env.local` / `export $(cat .env)` prefixes
  so environment variables are loaded correctly before deploying.
- **Executor** — runs the extracted command via `ProcessBuilder`/`bash -c`, in the working
  directory of the stack file (required for relative `.env` loading).
- **Live logs** — streams `docker service logs --follow` output to the browser in real time over
  Server-Sent Events (SSE).
- **Image picker** — in the built-in editor, each `image:` line gets a dropdown populated from
  `docker images`, so you can retag a service to a locally available version without typing.

## Features

| Action | What it does |
|---|---|
| Deploy | Runs the exact command parsed from the stack file's comment |
| Remove | `docker stack rm <stack>` |
| Status | `docker stack ps <stack>` — task table |
| Services | `docker stack services <stack>` — replicas per service |
| Scale | `docker service scale <service>=<n>` |
| Logs | Live tail per service via SSE |
| Editor | Edit the YAML inline, with an image-tag dropdown per `image:` line |
| Global Docker panel | `docker ps`, `docker images`, `docker stack ls`, `docker stats`, `docker system prune` |

## Tech stack

Spring Boot 3.3, Thymeleaf, Bootstrap 5 (no separate frontend build), Server-Sent Events for log
streaming, plain `ProcessBuilder` for shell execution — no extra Docker client library.

## Configuration

```yaml
# src/main/resources/application.yml
app:
  scan-root: /Users/you/IdeaProjects   # folder to scan for docker-stack.yml files
  os: MAC                              # MAC | LINUX | WINDOWS
  max-scan-depth: 5

server:
  port: 7892
```

## Running it

```bash
mvn spring-boot:run
```

Then open `http://localhost:7892`.

## Non-goals

- Does not manage plain `docker-compose.yml` projects — Swarm stacks only, for now.
- Single-user, local-only tool — no authentication, not meant to run on a shared server.
- Not a replacement for Portainer in production environments.

## Project layout

```
docker-stack-manager/
├── src/main/java/com/personal/dockermanager/
│   ├── config/      # AppProperties, OsType
│   ├── domain/      # StackFile, ServiceInfo, ContainerInfo, ImageEntry, CommandResult
│   ├── service/      # ScannerService, StackParserService, DockerCommandService, ImageService
│   └── web/rest/     # StackController, ContainerController, GlobalDockerController, ApiController
├── src/main/resources/
│   ├── templates/    # index.html, editor.html, containers.html, logs.html
│   └── application.yml
└── docs/             # design notes
```

## Status

Personal tool, used daily to manage the `concorsi-smart-*` stacks locally. Windows support is not
implemented yet (Mac/Linux only).

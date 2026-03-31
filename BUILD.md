# WireGuard Control Plane - Build Guide

## Overview

This project uses a two-stage Docker build approach to optimize development workflow:

1. **Base Image** (`wg-control-plane-base`) - Contains Ubuntu 22.04, Java 17, WireGuard tools, and system dependencies
2. **Application Image** (`wg-control-plane`) - Builds on the base image and adds the application code

## Quick Start

### Build Everything
```bash
make all
```

### Build Only Base Image (for sharing or caching)
```bash
make base
```

### Build Only Application (after base exists)
```bash
make app
```

## Available Commands

Run `make help` to see all available commands:

```bash
make help
```

### Common Commands

| Command | Description |
|---------|-------------|
| `make all` | Build both base and application images |
| `make base` | Build only the base development image |
| `make app` | Build only the application image |
| `make clean` | Remove all project images |
| `make list` | List all project images |
| `make run` | Run the application container |
| `make stop` | Stop the running container |
| `make logs` | Show container logs |
| `make shell` | Open shell in application container |
| `make dev-build` | Quick rebuild for development (uses cache) |

## Development Workflow

### Initial Setup
```bash
# Build the base image (only needed once or when system dependencies change)
make base

# Build and run the application
make app
make run
```

### Daily Development
```bash
# For code changes, only rebuild the application layer
make dev-build
make run
```

### When System Dependencies Change
```bash
# Rebuild everything from scratch
make clean
make all
```

## Benefits

- **Faster builds**: Base image with system dependencies is built once and reused
- **Smaller layers**: Application changes don't require rebuilding system packages
- **Better caching**: Docker layer caching is more effective
- **Shared base**: Base image can be pushed to a registry and shared across team

## Docker Registry Usage

### Push Base Image to Registry
```bash
# Tag for registry
docker tag wg-control-plane-base:latest your-registry/wg-control-plane-base:latest

# Push to registry
docker push your-registry/wg-control-plane-base:latest
```

### Use Registry Base Image
Edit the `Dockerfile` to use the registry image:
```dockerfile
FROM your-registry/wg-control-plane-base:latest
```

## Troubleshooting

### Base Image Not Found
If you get an error about the base image not being found:
```bash
make base
```

### Clean Start
To completely restart:
```bash
make clean
make all
```

### Check Image Sizes
```bash
make list
```
# WireGuard Control Plane - Docker Build Makefile

# Image names and tags
BASE_IMAGE_NAME := wg-control-plane-base
APP_IMAGE_NAME := wg-control-plane
BASE_IMAGE_TAG := latest
APP_IMAGE_TAG := latest

# Docker compose project name
COMPOSE_PROJECT_NAME := wg-control-plane

# Colors for output
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
RED := \033[0;31m
NC := \033[0m

.PHONY: help base app all clean list run stop logs dev-up dev-down

# Default target
all: base app

help: ## Show this help message
	@echo "WireGuard Control Plane - Docker Build Commands"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(BLUE)%-15s$(NC) %s\n", $$1, $$2}'

base: ## Build the base development image
	@echo -e "$(BLUE)Building base image: $(BASE_IMAGE_NAME):$(BASE_IMAGE_TAG)$(NC)"
	docker build -f Dockerfile.base -t $(BASE_IMAGE_NAME):$(BASE_IMAGE_TAG) .
	@echo -e "$(GREEN)✓ Base image built successfully$(NC)"
	@echo -e "$(BLUE)Base image size:$(NC) $$(docker images $(BASE_IMAGE_NAME):$(BASE_IMAGE_TAG) --format '{{.Size}}')"

app: check-base ## Build the application image (requires base image)
	@echo -e "$(BLUE)Building application image: $(APP_IMAGE_NAME):$(APP_IMAGE_TAG)$(NC)"
	docker build -f Dockerfile -t $(APP_IMAGE_NAME):$(APP_IMAGE_TAG) .
	@echo -e "$(GREEN)✓ Application image built successfully$(NC)"
	@echo -e "$(BLUE)Application image size:$(NC) $$(docker images $(APP_IMAGE_NAME):$(APP_IMAGE_TAG) --format '{{.Size}}')"

check-base: ## Check if base image exists, build if not
	@if ! docker image inspect $(BASE_IMAGE_NAME):$(BASE_IMAGE_TAG) >/dev/null 2>&1; then \
		echo -e "$(YELLOW)⚠ Base image not found. Building base image first...$(NC)"; \
		$(MAKE) base; \
	fi

clean: ## Remove all project images
	@echo -e "$(BLUE)Cleaning up images...$(NC)"
	-docker rmi $(APP_IMAGE_NAME):$(APP_IMAGE_TAG) 2>/dev/null || true
	-docker rmi $(BASE_IMAGE_NAME):$(BASE_IMAGE_TAG) 2>/dev/null || true
	docker image prune -f >/dev/null 2>&1 || true
	@echo -e "$(GREEN)✓ Images cleaned up$(NC)"

list: ## List all project images
	@echo -e "$(BLUE)Current project images:$(NC)"
	@docker images | grep -E "($(BASE_IMAGE_NAME)|$(APP_IMAGE_NAME))" || echo "No project images found"

run: app ## Run the application container
	@echo -e "$(BLUE)Starting application container...$(NC)"
	docker run --rm --name wg-control-plane-dev \
		-p 8080:8080 \
		--privileged \
		-v /dev/net/tun:/dev/net/tun \
		$(APP_IMAGE_NAME):$(APP_IMAGE_TAG)

run-detached: app ## Run the application container in detached mode
	@echo -e "$(BLUE)Starting application container in detached mode...$(NC)"
	docker run -d --name wg-control-plane-dev \
		-p 8080:8080 \
		--privileged \
		-v /dev/net/tun:/dev/net/tun \
		$(APP_IMAGE_NAME):$(APP_IMAGE_TAG)
	@echo -e "$(GREEN)✓ Container started. Use 'make logs' to view logs$(NC)"

stop: ## Stop the running application container
	@echo -e "$(BLUE)Stopping application container...$(NC)"
	-docker stop wg-control-plane-dev 2>/dev/null || true
	-docker rm wg-control-plane-dev 2>/dev/null || true
	@echo -e "$(GREEN)✓ Container stopped$(NC)"

logs: ## Show logs from the running container
	docker logs -f wg-control-plane-app

dev-up: ## Start development environment with docker-compose
	@echo -e "$(BLUE)Starting development environment...$(NC)"
	docker-compose up -d
	@echo -e "$(GREEN)✓ Development environment started$(NC)"

dev-down: ## Stop development environment
	@echo -e "$(BLUE)Stopping development environment...$(NC)"
	docker-compose down
	@echo -e "$(GREEN)✓ Development environment stopped$(NC)"

rebuild: clean all ## Clean and rebuild all images

# Development targets
dev-build: ## Quick rebuild for development (uses cache)
	@echo -e "$(BLUE)Quick development build...$(NC)"
	docker build -f Dockerfile -t $(APP_IMAGE_NAME):$(APP_IMAGE_TAG) .
	@echo -e "$(GREEN)✓ Development build completed$(NC)"

local-build:
	./gradlew bootJar
	docker build -f Dockerfile.dev -t wg-control-plane:dev .

shell: ## Open shell in the application container
	docker exec -it wg-control-plane-app sh

base-shell: ## Open shell in the base image for debugging
	docker run --rm -it \
		$(BASE_IMAGE_NAME):$(BASE_IMAGE_TAG) \
		bash
# Flowpanel developer shortcuts. Requires: Docker, Java 21, Node 22+, Python 3.11+.
SHELL := bash
BACKEND_URL ?= http://localhost:8080
MVNW := cd backend && ./mvnw -q

.PHONY: help dev db-up db-down db-reset backend frontend test test-backend test-frontend eval seed

help:
	@echo "Targets: dev db-up db-down db-reset backend frontend test eval seed"

db-up:
	docker compose up -d --wait postgres

db-down:
	docker compose down

db-reset:
	docker compose down -v
	docker compose up -d --wait postgres

backend: db-up
	cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

frontend:
	cd frontend && npm install && npm run dev

# Starts Postgres, the backend (mock AI profile) and the frontend together. Ctrl+C stops both.
dev: db-up
	cd frontend && npm install
	@trap 'kill 0' EXIT; \
	  (cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run) & \
	  (cd frontend && npm run dev) & \
	  wait

test: test-backend test-frontend

test-backend:
	cd backend && ./mvnw verify

test-frontend:
	cd frontend && npm install && npm run lint && npm run typecheck && npm run test

# Starts the backend in mock profile on a throwaway database, then runs the full eval suite.
eval:
	bash evals/run_mock.sh

# Re-seeds the demo data through the admin endpoint (backend must be running).
seed:
	python -m evals.seed --base-url $(BACKEND_URL)

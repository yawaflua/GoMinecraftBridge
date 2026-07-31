GO_MODULE := github.com/yawaflua/GoMinecraftBridge/backend
PROTO_FILE := api/project/v1/project.proto
TOOLS_DIR ?= $(CURDIR)/.tools
GOOSE := $(TOOLS_DIR)/goose
MIGRATIONS_DIR := internal/adapters/psql/migrations

.PHONY: tools generate migrate-up migrate-down migrate-status migrate-create

tools:
	mkdir -p $(TOOLS_DIR)
	GOBIN=$(TOOLS_DIR) go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.11
	GOBIN=$(TOOLS_DIR) go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.6.2
	GOBIN=$(TOOLS_DIR) go install github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-grpc-gateway@v2.29.0
	GOBIN=$(TOOLS_DIR) go install github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-openapiv2@v2.29.0
	GOBIN=$(TOOLS_DIR) go install github.com/pressly/goose/v3/cmd/goose@v3.27.3

generate: tools
	mkdir -p docs/swagger
	PATH="$(TOOLS_DIR):$$PATH" protoc -I . -I /usr/include \
		--go_out=. --go_opt=module=$(GO_MODULE) \
		--go-grpc_out=. --go-grpc_opt=module=$(GO_MODULE) \
		--grpc-gateway_out=. --grpc-gateway_opt=module=$(GO_MODULE) \
		--openapiv2_out=docs/swagger \
		--openapiv2_opt=allow_merge=true \
		--openapiv2_opt=merge_file_name=project \
		$(PROTO_FILE)

migrate-up: tools
	$(GOOSE) -dir $(MIGRATIONS_DIR) postgres "$(DATABASE_URL)" up

migrate-down: tools
	$(GOOSE) -dir $(MIGRATIONS_DIR) postgres "$(DATABASE_URL)" down

migrate-status: tools
	$(GOOSE) -dir $(MIGRATIONS_DIR) postgres "$(DATABASE_URL)" status

migrate-create: tools
	$(GOOSE) -dir $(MIGRATIONS_DIR) create "$(NAME)" sql

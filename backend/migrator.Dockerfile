FROM golang:1.25.8-alpine AS build

RUN CGO_ENABLED=0 GOBIN=/out go install github.com/pressly/goose/v3/cmd/goose@v3.27.3

FROM alpine:3.22

RUN apk add --no-cache ca-certificates

COPY --from=build /out/goose /usr/local/bin/goose
COPY internal/adapters/psql/migrations /migrations

CMD ["sh", "-c", "exec goose -dir /migrations postgres \"postgres://${PSQL_USERNAME:?PSQL_USERNAME is required}:${PSQL_PASSWORD:?PSQL_PASSWORD is required}@${PSQL_HOST:?PSQL_HOST is required}:${PSQL_PORT:-5432}/${PSQL_DATABASE:?PSQL_DATABASE is required}?sslmode=${PSQL_SSLMODE:-disable}\" up"]

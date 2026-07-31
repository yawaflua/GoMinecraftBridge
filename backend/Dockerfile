FROM golang:1.25.8-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/gmb ./cmd/app/main.go

FROM alpine:latest

COPY --from=build /out/gmb .
EXPOSE 8080

CMD ["./gmb"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null || exit 1
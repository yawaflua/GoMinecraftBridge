package httpapi

import (
	"net/http"

	swaggerdoc "github.com/yawaflua/GoMinecraftBridge/backend/docs/swagger"
)

func serveSwaggerJSON(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write(swaggerdoc.Document)
}

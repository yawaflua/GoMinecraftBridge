package api

import (
	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
)

type Service struct {
	db            adapters.DB
	authenticator *auth.Authenticator
	projectv1.UnimplementedGBMBackendServer
}

var _ projectv1.GBMBackendServer = (*Service)(nil)

type ServiceDependencies struct {
	DB            adapters.DB
	Authenticator *auth.Authenticator
}

func NewService(dependencies ServiceDependencies) *Service {
	return &Service{
		db:            dependencies.DB,
		authenticator: dependencies.Authenticator,
	}
}

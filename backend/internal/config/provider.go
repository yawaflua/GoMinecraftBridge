package config

import (
	"context"

	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters/mongo"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters/psql"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/api"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
)

type Provider struct {
	db *adapters.DB
	c  *C

	gmbServer projectv1.GBMBackendServer
	auth      *auth.Authenticator
}

func (a *Provider) Authenticator(ctx context.Context) *auth.Authenticator {
	if a.auth == nil {
		config := a.C().Auth
		authenticator, err := auth.New(auth.Config{
			Secret:          config.Secret,
			Issuer:          config.Issuer,
			Audience:        config.Audience,
			AccessTokenTTL:  config.AccessTokenTTL,
			RefreshTokenTTL: config.RefreshTokenTTL,
			Leeway:          config.Leeway,
		}, *a.DB(ctx))
		if err != nil {
			panic(err)
		}
		a.auth = authenticator
	}

	return a.auth
}

func (a *Provider) C() *C {
	if a.c == nil {
		a.c = MustLoad()
	}
	return a.c
}

func (a *Provider) GmbServer(ctx context.Context) projectv1.GBMBackendServer {
	if a.gmbServer == nil {
		a.gmbServer = api.NewService(api.ServiceDependencies{
			DB:            *a.DB(ctx),
			Authenticator: a.Authenticator(ctx),
		})
	}
	return a.gmbServer
}

func (a *Provider) DB(ctx context.Context) *adapters.DB {
	if a.db == nil {
		if a.C().UsePsql {
			db, err := psql.New(ctx, a.C().Postgres)
			if err != nil {
				panic(err)
			}
			a.db = &db
		} else {
			db, err := mongo.New(ctx, a.C().Mongo)
			if err != nil {
				panic(err)
			}
			a.db = &db
		}
	}
	return a.db
}

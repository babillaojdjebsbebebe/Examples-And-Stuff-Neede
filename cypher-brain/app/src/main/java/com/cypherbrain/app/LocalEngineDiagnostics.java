package com.cypherbrain.app;

import android.content.Context;

import java.util.List;

public final class LocalEngineDiagnostics {
    private LocalEngineDiagnostics() {
    }

    public static Result run(Context context) {
        try {
            BridgeRepository bridges = new BridgeRepository(context);
            RhymeEngine rhymes = new RhymeEngine(context);

            if (bridges.potentialConnections() < 10000) {
                return Result.fail("grafo incompleto: " + bridges.potentialConnections());
            }
            if (bridges.domainCount() < 20) {
                return Result.fail("pocos universos semánticos: " + bridges.domainCount());
            }
            if (rhymes.familyCount() < 40) {
                return Result.fail("corpus fonético incompleto: " + rhymes.familyCount());
            }

            List<String> moto = rhymes.rhymePack("moto", 10);
            if (moto.size() != 10 || !"oto".equals(rhymes.familyFor("moto"))) {
                return Result.fail("familia -oto no disponible");
            }

            List<String> velocidad = rhymes.rhymePack("velocidad", 10);
            if (velocidad.size() != 10 || !"idad".equals(rhymes.familyFor("velocidad"))) {
                return Result.fail("familia -idad no disponible");
            }

            List<String> routes = bridges.bridgesFor("moto", 4, 2);
            if (routes.size() < 4) {
                return Result.fail("puentes de moto insuficientes");
            }

            return Result.ok(
                    bridges.potentialConnections(),
                    bridges.domainCount(),
                    rhymes.familyCount()
            );
        } catch (Throwable error) {
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = error.getClass().getSimpleName();
            }
            return Result.fail(message);
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final int bridges;
        public final int domains;
        public final int rhymeFamilies;

        private Result(boolean ok, String message, int bridges, int domains, int rhymeFamilies) {
            this.ok = ok;
            this.message = message;
            this.bridges = bridges;
            this.domains = domains;
            this.rhymeFamilies = rhymeFamilies;
        }

        static Result ok(int bridges, int domains, int rhymeFamilies) {
            return new Result(true, "OK", bridges, domains, rhymeFamilies);
        }

        static Result fail(String message) {
            return new Result(false, message, 0, 0, 0);
        }
    }
}

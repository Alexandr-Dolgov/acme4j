/*
 * acme4j - Java ACME client
 *
 * Copyright (C) 2015 Richard "Shred" Körber
 *   http://acme4j.shredzone.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.shredzone.acme4j.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.challenge.OutOfBand01Challenge;
import org.shredzone.acme4j.challenge.TlsSni02Challenge;
import org.shredzone.acme4j.connector.Connection;
import org.shredzone.acme4j.connector.DefaultConnection;
import org.shredzone.acme4j.connector.HttpConnector;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.JSON;

/**
 * Abstract implementation of {@link AcmeProvider}. It consists of a challenge
 * registry and a standard {@link HttpConnector}.
 * <p>
 * Implementing classes must implement at least {@link AcmeProvider#accepts(URI)}
 * and {@link AbstractAcmeProvider#resolve(URI)}.
 */
public abstract class AbstractAcmeProvider implements AcmeProvider {

    private static final Map<String, Constructor<? extends Challenge>> CHALLENGES = challengeMap();

    @Override
    public Connection connect() {
        return new DefaultConnection(createHttpConnector());
    }

    @Override
    public JSON directory(Session session, URI serverUri) throws AcmeException {
        try (Connection conn = connect()) {
            conn.sendRequest(resolve(serverUri), session);
            conn.accept(HttpURLConnection.HTTP_OK);

            // use nonce header if there is one, saves a HEAD request...
            conn.updateSession(session);

            return conn.readJsonResponse();
        }
    }

    @SuppressWarnings("deprecation") // must still provide deprecated challenges
    private static Map<String, Constructor<? extends Challenge>> challengeMap() {
        Map<String, Constructor<? extends Challenge>> map = new HashMap<>();

        try {
            map.put(Dns01Challenge.TYPE,
                            Dns01Challenge.class.getConstructor(Session.class));

            map.put(org.shredzone.acme4j.challenge.TlsSni01Challenge.TYPE,
                            org.shredzone.acme4j.challenge.TlsSni01Challenge.class.getConstructor(Session.class));

            map.put(TlsSni02Challenge.TYPE,
                            TlsSni02Challenge.class.getConstructor(Session.class));

            map.put(Http01Challenge.TYPE,
                            Http01Challenge.class.getConstructor(Session.class));

            map.put(OutOfBand01Challenge.TYPE,
                            OutOfBand01Challenge.class.getConstructor(Session.class));
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("Could not find Challenge constructor", ex);
        }

        return Collections.unmodifiableMap(map);
    }

    @Override
    public Challenge createChallenge(Session session, String type) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(type, "type");

        Constructor<? extends Challenge> constructor = CHALLENGES.get(type);
        if (constructor == null) {
            return null;
        }

        try {
            return constructor.newInstance(session);
        } catch (InvocationTargetException | IllegalAccessException
                    | InstantiationException ex) {
            throw new IllegalStateException(
                    "Could not instantiate a Challenge for type " + type, ex);
        }
    }

    /**
     * Creates a {@link HttpConnector}.
     * <p>
     * Subclasses may override this method to configure the {@link HttpConnector}.
     */
    protected HttpConnector createHttpConnector() {
        return new HttpConnector();
    }

}

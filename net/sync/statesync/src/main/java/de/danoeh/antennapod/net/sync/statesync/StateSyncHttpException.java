package de.danoeh.antennapod.net.sync.statesync;

import java.io.IOException;

final class StateSyncHttpException extends IOException {
    enum Reason {
        AUTHENTICATION,
        SERVER,
        INVALID_RESPONSE
    }

    private final Reason reason;

    StateSyncHttpException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }
}

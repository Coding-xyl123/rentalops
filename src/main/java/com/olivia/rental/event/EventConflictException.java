
package com.olivia.rental.event;

public class EventConflictException extends RuntimeException {

    public EventConflictException(String message) {
        super(message);
    }
}

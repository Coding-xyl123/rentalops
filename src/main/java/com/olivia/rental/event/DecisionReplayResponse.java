package com.olivia.rental.event;

import java.util.List;

public record DecisionReplayResponse(
        Event triggerEvent,
        Event recommendation,
        List<Event> decisions
) {
}

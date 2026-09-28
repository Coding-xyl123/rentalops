# Rental Operations Event Model V1

## System Goal

Use operationl and guest signals to recommend actions before issues become guest complaints

## Event 1: GuestQuestionReceived

Represents a question received from a guest.

### Common Fields

- `eventId` — unique identifier for this event
- `timestamp` — when the event occurred
- `propertyId` — which rental property this event belongs to
- `source` — where the event came from
- `payload` — event-specific data

## Event 2: CleaningIssueDetected

Represents a cleaning-related issue detected at a rental property.

### Common Fields

- `eventId` — unique identifier for this event
- `timestamp` — when the event occurred
- `propertyId` — which rental property this event belongs to
- `source` — where the event came from
- `payload` — event-specific data

### Example

````json
{
  "eventId": "evt_002",
  "timestamp": "2026-09-28T01:40:00Z",
  "propertyId": "property_001",
  "source": "cleaner_report",
  "payload": {
    "issue": "Bathroom sink is draining slowly"
  }
}

## Event 3: MaintenanceIssueDetected

Represents a maintenance-related issue detected at a rental property.

### Common Fields

- `eventId` — unique identifier for this event
- `timestamp` — when the event occurred
- `propertyId` — which rental property this event belongs to
- `source` — where the event came from
- `payload` — event-specific data

### Example

```json
{
  "eventId": "evt_003",
  "timestamp": "2026-09-28T01:45:00Z",
  "propertyId": "property_001",
  "source": "cleaner_report",
  "payload": {
    "issue": "Bathroom light is not working"
  }
}
````

## Event 3: MaintenanceIssueDetected

Represents a maintenance-related issue detected at a rental property.

### Common Fields

- `eventId` — unique identifier for this event
- `timestamp` — when the event occurred
- `propertyId` — which rental property this event belongs to
- `source` — where the event came from
- `payload` — event-specific data

### Example

```json
{
  "eventId": "evt_003",
  "timestamp": "2026-09-28T01:45:00Z",
  "propertyId": "property_001",
  "source": "cleaner_report",
  "payload": {
    "issue": "Bathroom light is not working"
  }
}
```

## Event 4: RecommendationGenerated

Represents an action recommended by the system based on observed signals.

### Common Fields

- `eventId` — unique identifier for this event
- `timestamp` — when the event occurred
- `propertyId` — which rental property this event belongs to
- `source` — where the event came from
- `payload` — event-specific data

### Example

```json
{
  "eventId": "evt_004",
  "timestamp": "2026-09-28T01:50:00Z",
  "propertyId": "property_001",
  "source": "recommendation_engine",
  "payload": {
    "triggerEventId": "evt_003",
    "recommendedAction": "Replace bathroom light bulb before guest check-in"
  }
}
```

## Event 5: OperatorActionTaken

Represents an action actually taken by the rental operator in response to a recommendation.

### Common Fields

- `eventId` — unique identifier for this event
- `timestamp` — when the event occurred
- `propertyId` — which rental property this event belongs to
- `source` — where the event came from
- `payload` — event-specific data

### Example

```json
{
  "eventId": "evt_005",
  "timestamp": "2026-09-28T02:00:00Z",
  "propertyId": "property_001",
  "source": "operator",
  "payload": {
    "recommendationEventId": "evt_004",
    "actionTaken": "Replaced bathroom light bulb before guest check-in"
  }
}
```

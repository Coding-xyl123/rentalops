
package com.olivia.rental.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {
    List<Event> findByEventTypeOrderByTimestampDesc(String eventType);
    List<Event> findTop50ByOrderByTimestampDesc();
}

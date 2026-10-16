package com.example.helpdesk.activity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.helpdesk.ticket.TicketEvent;
import com.example.helpdesk.ticket.TicketEventType;

// Plain unit test of the listener's own logic; Kafka delivery itself is covered by
// HelpdeskFlowTests against a real broker.
@ExtendWith(MockitoExtension.class)
class TicketActivityListenerTests {

	@Mock
	private TicketActivityRepository activities;

	@InjectMocks
	private TicketActivityListener listener;

	private final TicketEvent event = TicketEvent.of(TicketEventType.STATUS_CHANGED, 5L, 2L,
			Map.of("from", "OPEN", "to", "RESOLVED"));

	@Test
	void storesNewEvents() {
		given(activities.existsByEventId(event.eventId())).willReturn(false);

		listener.on(event);

		then(activities).should().save(any(TicketActivity.class));
	}

	@Test
	void ignoresAnEventItHasAlreadyStored() {
		// The same event delivered a second time (Kafka is "at least once")
		given(activities.existsByEventId(event.eventId())).willReturn(true);

		listener.on(event);

		then(activities).should().existsByEventId(event.eventId());
		then(activities).shouldHaveNoMoreInteractions();
	}

}

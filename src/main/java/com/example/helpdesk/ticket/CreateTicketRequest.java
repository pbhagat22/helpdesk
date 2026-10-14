package com.example.helpdesk.ticket;

// reporterId comes from the request body until Spring Security (stage 4)
// lets us take it from the logged-in user.
public record CreateTicketRequest(String title, String description, Priority priority, Long reporterId) {
}

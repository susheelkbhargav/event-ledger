package com.eventledger.gateway.api;

import com.eventledger.gateway.api.dto.EventRequest;
import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.service.EventService;
import com.eventledger.gateway.service.SubmissionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody EventRequest request) {
        SubmissionResult result = eventService.submit(request);
        HttpStatus status = switch (result.outcome()) {
            case CREATED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.OK;
            case QUEUED -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(EventResponse.from(result.record()));
    }

    @GetMapping("/{eventId}")
    public EventResponse byId(@PathVariable String eventId) {
        return eventService.findById(eventId);
    }

    @GetMapping
    public List<EventResponse> byAccount(@RequestParam("account") String accountId, Pageable pageable) {
        return eventService.findByAccount(accountId, pageable);
    }
}

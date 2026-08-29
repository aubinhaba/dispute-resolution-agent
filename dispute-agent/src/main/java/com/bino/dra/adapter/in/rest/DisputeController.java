package com.bino.dra.adapter.in.rest;

import com.bino.dra.application.orchestration.DisputeSubmissionService;
import com.bino.dra.application.orchestration.Submission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/disputes")
public class DisputeController {

    private final DisputeSubmissionService submissions;

    public DisputeController(DisputeSubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping
    public ResponseEntity<DisputeCaseView> submit(@RequestBody SubmitDisputeRequest request) {
        Submission result = submissions.submit(request.toDomain());
        return ResponseEntity
                .status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(URI.create("/disputes/" + result.state().disputeId()))
                .body(DisputeCaseView.from(result.state()));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeCaseView> get(@PathVariable String disputeId) {
        return submissions.find(disputeId)
                .map(DisputeCaseView::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

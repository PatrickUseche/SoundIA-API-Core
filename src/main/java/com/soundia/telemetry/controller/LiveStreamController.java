package com.soundia.telemetry.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.soundia.telemetry.stream.LiveStreamService;

@RestController
@RequestMapping("/api/v1/midi")
public class LiveStreamController {

    private final LiveStreamService liveStream;

    public LiveStreamController(LiveStreamService liveStream) {
        this.liveStream = liveStream;
    }

    // El navegador se conecta con EventSource y recibe eventos "midi" y "decision" en vivo.
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return liveStream.register();
    }
}

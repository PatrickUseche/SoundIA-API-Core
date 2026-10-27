package com.soundia.engine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lo que se sabe del motor de IA segun las ultimas llamadas que se le hicieron. */
@RestController
@RequestMapping("/api/v1/engine")
public class EngineStatusController {

    /** `state` es UNKNOWN, UP o DOWN; no consulta al motor, solo informa de la ultima llamada. */
    @GetMapping("/status")
    public EngineStatus.Snapshot status() {
        return EngineStatus.getInstance().snapshot();
    }
}

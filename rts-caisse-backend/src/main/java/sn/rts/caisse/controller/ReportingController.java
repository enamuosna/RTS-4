package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.DashboardResponse;
import sn.rts.caisse.service.ReportingService;

import java.time.LocalDate;

/**
 * Reporting et tableaux de bord pour la supervision et la direction.
 * Réservé aux rôles ADMIN et SUPERVISEUR (voir SecurityConfig).
 */
@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Dashboards et agrégats (ADMIN / SUPERVISEUR)")
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/dashboard")
    @Operation(summary = "Tableau de bord d'une journée (jour courant par défaut)")
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportingService.dashboard(date));
    }
}

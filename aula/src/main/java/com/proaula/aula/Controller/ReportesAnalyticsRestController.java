package com.proaula.aula.Controller;

import com.proaula.aula.Service.ReportesAnalyticsService;
import com.proaula.aula.dto.ReportesAnalyticsResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reportes")
public class ReportesAnalyticsRestController {
    private final ReportesAnalyticsService reportesAnalyticsService;

    public ReportesAnalyticsRestController(ReportesAnalyticsService reportesAnalyticsService) {
        this.reportesAnalyticsService = reportesAnalyticsService;
    }

    @GetMapping("/indicadores")
    public ResponseEntity<ReportesAnalyticsResponse> obtenerIndicadores() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(reportesAnalyticsService.obtenerIndicadores());
    }
}

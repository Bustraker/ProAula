package com.proaula.aula.dto;

import java.util.List;

public record IncidentePageResponse(
        List<IncidentePanelResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}

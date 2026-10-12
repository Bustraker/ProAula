package com.proaula.aula.Controller;

import java.util.List;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Service.BusService;
import com.proaula.aula.dto.BusConsultaDto;
import com.proaula.aula.dto.BusResponseDto;

@RestController
@RequestMapping("/api/buses")
public class BusRestController {
    @Autowired
    private BusService busService;

    @GetMapping
    public List<BusResponseDto> getAllBuses() {
        return busService.getAllBusResponses();
    }

    @GetMapping("/todos")
    public List<BusConsultaDto> getAllBusesWithRuta(Authentication authentication) {
        boolean autenticado = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return busService.getBusConsultations(autenticado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusResponseDto> getBusById(@PathVariable Long id) {
        BusResponseDto bus = busService.getBusResponse(id);
        return bus != null ? ResponseEntity.ok(bus) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<BusResponseDto> createBus(@Valid @RequestBody Bus bus) {
        bus.setId(null);
        return ResponseEntity.status(201).body(busService.saveBusResponse(bus));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusResponseDto> updateBus(@PathVariable Long id, @Valid @RequestBody Bus bus) {
        if (busService.getBusById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        bus.setId(id);
        return ResponseEntity.ok(busService.saveBusResponse(bus));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBus(@PathVariable Long id) {
        if (busService.getBusResponse(id) == null) {
            return ResponseEntity.notFound().build();
        }
        if (busService.hasRelatedRecords(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        busService.deleteBus(id);
        return ResponseEntity.noContent().build();
    }
}
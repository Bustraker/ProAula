package com.proaula.aula.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Service.BusService;

@RestController
@RequestMapping("/api/buses")
public class BusRestController {
    @Autowired
    private BusService busService;

    @GetMapping
    public List<Bus> getAllBuses() {
        return busService.getAllBuses();
    }

    @GetMapping("/todos")
    public List<Map<String, Object>> getAllBusesWithRuta() {
        List<Bus> buses = busService.getAllBuses();
        return buses.stream().map(bus -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", bus.getId());
            map.put("placa", bus.getPlaca());
            map.put("modelo", bus.getModelo());
            map.put("color", bus.getColor());
            map.put("conductor", bus.getConductor());
            if (bus.getRuta() != null) {
                map.put("rutaId", bus.getRuta().getId());
                map.put("rutaNombre", bus.getRuta().getNombre());
            }
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public Bus getBusById(@PathVariable Long id) {
        return busService.getBusById(id);
    }

    @PostMapping
    public Bus createBus(@RequestBody Bus bus) {
        return busService.saveBus(bus);
    }

    @PutMapping("/{id}")
    public Bus updateBus(@PathVariable Long id, @RequestBody Bus bus) {
        bus.setId(id);
        return busService.saveBus(bus);
    }

    @DeleteMapping("/{id}")
    public void deleteBus(@PathVariable Long id) {
        busService.deleteBus(id);
    }
}
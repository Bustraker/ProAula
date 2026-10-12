package com.proaula.aula.Service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.AsientoRepository;
import com.proaula.aula.Repository.AsignacionRutaBusRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.exception.AulaException;
import com.proaula.aula.dto.BusConsultaDto;
import com.proaula.aula.dto.BusConsultaMapper;
import com.proaula.aula.dto.BusResponseDto;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusService {
    @Autowired
    private BusRepository busRepository;

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private AsignacionRutaBusRepository asignacionRutaBusRepository;

    @Autowired
    private PasajeRepository pasajeRepository;

    public List<Bus> getAllBuses() {
        return busRepository.findAllWithDetails();
    }

    public Bus getBusById(Long id) {
        return busRepository.findByIdWithDetails(id).orElse(null);
    }

    public Bus saveBus(Bus bus) {
        return busRepository.save(bus);
    }

    @Transactional(readOnly = true)
    public List<BusResponseDto> getAllBusResponses() {
        return busRepository.findAllWithDetails().stream()
                .map(BusResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusResponseDto getBusResponse(Long id) {
        return busRepository.findByIdWithDetails(id)
                .map(BusResponseDto::from)
                .orElse(null);
    }

    @Transactional
    public BusResponseDto saveBusResponse(Bus bus) {
        return BusResponseDto.from(busRepository.save(bus));
    }

    @Transactional(readOnly = true)
    public List<BusConsultaDto> getBusConsultations(boolean authenticated) {
        List<Bus> buses = busRepository.findAllWithDetails();
        return authenticated
                ? BusConsultaMapper.authenticatedBuses(buses)
                : BusConsultaMapper.publicRoutes(buses);
    }

    @Transactional
    public void deleteBus(Long id) {
        if (id == null || !busRepository.existsById(id)) {
            return;
        }
        if (hasRelatedRecords(id)) {
            throw new AulaException(
                "No se puede eliminar el bus porque tiene asientos, asignaciones o pasajes asociados.",
                "RESOURCE_IN_USE");
        }
        busRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean hasRelatedRecords(Long id) {
        return id != null && (asientoRepository.existsByBus_Id(id)
                || asignacionRutaBusRepository.existsByBus_Id(id)
                || pasajeRepository.existsByBus_Id(id));
    }
    
    public long count() {
        return busRepository.count();
    }
}
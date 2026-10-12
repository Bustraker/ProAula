package com.proaula.aula.Entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityConstraintsTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void paradaRequiresPositiveOrderAndCoordinatesWithinGeographicBounds() {
        Parada parada = new Parada();
        parada.setNombre("Parada de prueba");
        parada.setOrden(0);
        parada.setLatitud(91.0);
        parada.setLongitud(-181.0);

        assertThat(validator.validate(parada))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("orden", "latitud", "longitud");
    }

    @Test
    void busFieldsRespectModelLengths() {
        Bus bus = new Bus();
        bus.setPlaca("PLACA-TOO-LONG");
        bus.setModelo("M".repeat(51));

        assertThat(validator.validate(bus))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("placa", "modelo");
    }

    @Test
    void generatedCodesFitTheirDatabaseColumns() {
        Ruta ruta = new Ruta();
        Parada parada = new Parada();
        Bus bus = new Bus();

        ruta.generarCodigoSiHaceFalta();
        parada.generarCodigoSiHaceFalta();
        bus.generarNumeroInternoSiHaceFalta();

        assertThat(ruta.getCodigo()).startsWith("RUT-").hasSize(20);
        assertThat(parada.getCodigo()).startsWith("PAR-").hasSize(20);
        assertThat(bus.getNumeroInterno()).startsWith("BUS-").hasSize(20);
    }

    @Test
    void validCoordinatesAndPositiveStopOrderPassValidation() {
        Parada parada = new Parada();
        parada.setNombre("Parada de prueba");
        parada.setOrden(1);
        parada.setLatitud(10.4);
        parada.setLongitud(-75.5);

        assertThat(validator.validate(parada)).isEmpty();
    }

}

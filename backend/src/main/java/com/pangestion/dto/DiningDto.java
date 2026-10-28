package com.pangestion.dto;

import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.model.TableShape;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Plano de la sala de un bar o restaurante: zonas y mesas. Las medidas van en celdas de la cuadricula. */
public final class DiningDto {

    private DiningDto() {
    }

    public record AreaRequest(
            @NotBlank @Size(max = 60) String name,
            @Min(6) @Max(60) int width,
            @Min(4) @Max(40) int height) {
    }

    /** Mesa nueva o editada. Al crearla, la zona la decide la ruta (POST /api/dining/areas/{id}/tables). */
    public record TableRequest(
            @NotBlank @Size(max = 20) String name,
            @Min(1) @Max(30) int seats,
            @NotNull TableShape shape,
            @Min(0) int x,
            @Min(0) int y,
            @Min(1) @Max(12) int width,
            @Min(1) @Max(12) int height) {
    }

    public record TableResponse(Long id, Long areaId, String name, int seats, TableShape shape,
                                int x, int y, int width, int height) {

        public static TableResponse from(DiningTable table) {
            return new TableResponse(table.getId(), table.getArea().getId(), table.getName(), table.getSeats(),
                    table.getShape(), table.getX(), table.getY(), table.getWidth(), table.getHeight());
        }
    }

    /** Zona con sus mesas, para pintar el plano en una sola peticion. seats = plazas de todas sus mesas. */
    public record AreaResponse(Long id, String name, int width, int height, int sortOrder, int seats,
                               List<TableResponse> tables) {

        public static AreaResponse from(DiningArea area) {
            return new AreaResponse(area.getId(), area.getName(), area.getWidth(), area.getHeight(), area.getSortOrder(),
                    area.getTables().stream().mapToInt(DiningTable::getSeats).sum(),
                    area.getTables().stream().map(TableResponse::from).toList());
        }
    }
}

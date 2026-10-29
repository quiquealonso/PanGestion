package com.pangestion.service;

import com.pangestion.dto.DiningDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.repository.DiningAreaRepository;
import com.pangestion.repository.DiningTableRepository;
import com.pangestion.repository.TableSessionRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Plano de la sala (zonas y mesas) de la empresa del usuario. Solo para bares y restaurantes.
 * Reglas: nombres unicos en la empresa, cada mesa cabe en su zona y no pisa a otra.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiningService {

    private final DiningAreaRepository areaRepository;
    private final DiningTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<DiningDto.AreaResponse> findAreas() {
        return areaRepository.findAllByCompanyIdOrderBySortOrderAscNameAsc(currentUser.companyId()).stream()
                .map(DiningDto.AreaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DiningDto.AreaResponse findArea(Long id) {
        return DiningDto.AreaResponse.from(getArea(id));
    }

    public DiningDto.AreaResponse createArea(DiningDto.AreaRequest request) {
        Company company = diningCompany();
        String name = request.name().trim();
        if (areaRepository.existsByCompanyIdAndNameIgnoreCase(company.getId(), name)) {
            throw new BusinessException("Ya existe una zona con el nombre " + name);
        }
        DiningArea area = new DiningArea();
        area.setCompany(company);
        area.setName(name);
        area.setWidth(request.width());
        area.setHeight(request.height());
        area.setSortOrder(areaRepository.nextSortOrder(company.getId()));
        return DiningDto.AreaResponse.from(areaRepository.save(area));
    }

    /** Cambia nombre y tamano. No se puede encoger la zona si alguna mesa quedaria fuera. */
    public DiningDto.AreaResponse updateArea(Long id, DiningDto.AreaRequest request) {
        DiningArea area = getArea(id);
        String name = request.name().trim();
        if (areaRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(currentUser.companyId(), name, id)) {
            throw new BusinessException("Ya existe una zona con el nombre " + name);
        }
        area.setName(name);
        area.setWidth(request.width());
        area.setHeight(request.height());
        for (DiningTable table : area.getTables()) {
            if (!area.fits(table)) {
                throw new BusinessException("La mesa " + table.getName() + " quedaria fuera de la zona: muevela antes");
            }
        }
        return DiningDto.AreaResponse.from(area);
    }

    /** Guarda el nuevo orden de las zonas (las pestanas del plano). */
    public List<DiningDto.AreaResponse> reorderAreas(List<Long> ids) {
        for (int position = 0; position < ids.size(); position++) {
            getArea(ids.get(position)).setSortOrder(position);
        }
        return findAreas();
    }

    /** Borra la zona con todas sus mesas, si ninguna esta ocupada. */
    public void deleteArea(Long id) {
        DiningArea area = getArea(id);
        if (sessionRepository.existsOpenInArea(id)) {
            throw new BusinessException("Hay mesas ocupadas en " + area.getName() + ": liberalas antes de borrar la zona");
        }
        areaRepository.delete(area);
    }

    public DiningDto.TableResponse createTable(Long areaId, DiningDto.TableRequest request) {
        DiningArea area = getArea(areaId);
        checkTableName(request.name().trim(), null);
        DiningTable table = new DiningTable();
        table.setCompany(area.getCompany());
        table.setArea(area);
        apply(table, request);
        checkPlacement(area, table);
        area.getTables().add(table);
        return DiningDto.TableResponse.from(tableRepository.save(table));
    }

    /** Edita la mesa: datos, posicion y tamano (al arrastrarla o redimensionarla en el editor). */
    public DiningDto.TableResponse updateTable(Long id, DiningDto.TableRequest request) {
        DiningTable table = getTable(id);
        checkTableName(request.name().trim(), id);
        apply(table, request);
        checkPlacement(table.getArea(), table);
        return DiningDto.TableResponse.from(table);
    }

    public void deleteTable(Long id) {
        DiningTable table = getTable(id);
        if (sessionRepository.findOpenWithTable(id, -1L).isPresent()) {
            throw new BusinessException("La mesa " + table.getName() + " esta ocupada: liberala antes de borrarla");
        }
        table.getArea().getTables().remove(table);
    }

    private void checkTableName(String name, Long id) {
        Long companyId = currentUser.companyId();
        boolean taken = id == null
                ? tableRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name)
                : tableRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, name, id);
        if (taken) {
            throw new BusinessException("Ya existe una mesa con el nombre " + name);
        }
    }

    private static void checkPlacement(DiningArea area, DiningTable table) {
        if (!area.fits(table)) {
            throw new BusinessException("La mesa " + table.getName() + " se sale de la zona " + area.getName());
        }
        DiningTable other = area.overlapping(table);
        if (other != null) {
            throw new BusinessException("La mesa " + table.getName() + " se solapa con la mesa " + other.getName());
        }
    }

    /** Empresa del usuario, si es un bar o restaurante. */
    private Company diningCompany() {
        Company company = companyService.current();
        if (company.getType() == BusinessType.PANADERIA) {
            throw new BusinessException("El plano de mesas solo esta disponible para bares y restaurantes");
        }
        return company;
    }

    DiningArea getArea(Long id) {
        return areaRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Zona", id));
    }

    DiningTable getTable(Long id) {
        return tableRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Mesa", id));
    }

    private static void apply(DiningTable table, DiningDto.TableRequest request) {
        table.setName(request.name().trim());
        table.setSeats(request.seats());
        table.setShape(request.shape());
        table.setX(request.x());
        table.setY(request.y());
        table.setWidth(request.width());
        table.setHeight(request.height());
    }
}

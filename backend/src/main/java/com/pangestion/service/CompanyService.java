package com.pangestion.service;

import com.pangestion.dto.CompanyDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.repository.CompanyRepository;
import com.pangestion.repository.CustomerRepository;
import com.pangestion.repository.OrderRepository;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.CurrentUser;
import com.pangestion.storage.ImageStorage;
import com.pangestion.storage.UploadedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Empresas (panaderias). El SUPERADMIN las da de alta y las gestiona;
 * cada ADMIN puede consultar y editar los datos y el horario de la suya.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final CurrentUser currentUser;
    private final ImageStorage imageStorage;

    @Value("${app.uploads.max-bytes}")
    private long maxImageBytes;

    // ----- SUPERADMIN: todas las empresas -----

    @Transactional(readOnly = true)
    public List<CompanyDto.Response> findAll() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyDto.Response findById(Long id) {
        return toResponse(getEntity(id));
    }

    public CompanyDto.Response create(CompanyDto.Request request) {
        if (companyRepository.existsByTaxIdIgnoreCase(request.taxId())) {
            throw new BusinessException("Ya existe una empresa con el CIF " + request.taxId());
        }
        Company company = new Company();
        apply(company, request);
        return toResponse(companyRepository.save(company));
    }

    public CompanyDto.Response update(Long id, CompanyDto.Request request) {
        Company company = getEntity(id);
        if (companyRepository.existsByTaxIdIgnoreCaseAndIdNot(request.taxId(), id)) {
            throw new BusinessException("Ya existe una empresa con el CIF " + request.taxId());
        }
        apply(company, request);
        return toResponse(company);
    }

    public void delete(Long id) {
        Company company = getEntity(id);
        if (orderRepository.countByCompanyId(id) > 0 || userRepository.countByCompanyId(id) > 0) {
            throw new BusinessException("La empresa tiene pedidos o usuarios: desactivala en lugar de eliminarla");
        }
        companyRepository.delete(company);
    }

    // ----- Empresa del usuario logueado -----

    @Transactional(readOnly = true)
    public CompanyDto.Response findCurrent() {
        return toResponse(current());
    }

    /** El ADMIN edita los datos de su empresa, pero el tipo de negocio solo lo cambia el SUPERADMIN. */
    public CompanyDto.Response updateCurrent(CompanyDto.Request request) {
        Company company = current();
        BusinessType type = company.getType();
        CompanyDto.Response response = update(company.getId(), request);
        company.setType(type);
        return CompanyDto.Response.from(company, response.userCount(), response.customerCount());
    }

    /**
     * Sube o sustituye el logo de la empresa (JPG, PNG o WEBP, mismo limite que las fotos de producto).
     * Se guarda en {empresa}/logo/{nombre aleatorio} y se borra el anterior.
     */
    public CompanyDto.Response uploadLogo(MultipartFile file) {
        Company company = current();
        UploadedImage image = UploadedImage.read(file, maxImageBytes);
        String previous = company.getLogoPath();
        company.setLogoPath(imageStorage.save(company.getId() + "/logo", image.data(), image.type()));
        imageStorage.delete(previous);
        return toResponse(company);
    }

    public CompanyDto.Response removeLogo() {
        Company company = current();
        imageStorage.delete(company.getLogoPath());
        company.setLogoPath(null);
        return toResponse(company);
    }

    /** Empresa del usuario logueado. */
    Company current() {
        return getEntity(currentUser.companyId());
    }

    // ----- Web publica -----

    @Transactional(readOnly = true)
    public List<CompanyDto.PublicResponse> findActive() {
        return companyRepository.findByActiveTrueOrderByNameAsc().stream().map(CompanyDto.PublicResponse::from).toList();
    }

    /** Franjas de recogida de un dia, indicando si quedan huecos. Las pasadas o llenas no estan disponibles. */
    @Transactional(readOnly = true)
    public List<CompanyDto.Slot> getSlots(Long companyId, LocalDate date) {
        Company company = getEntity(companyId);
        LocalDateTime now = LocalDateTime.now();
        List<CompanyDto.Slot> slots = new ArrayList<>();
        for (LocalTime time : slotTimes(company)) {
            long booked = orderRepository.countInSlot(company, date, time, -1L);
            int remaining = (int) Math.max(0, company.getOrdersPerSlot() - booked);
            boolean past = LocalDateTime.of(date, time).isBefore(now);
            slots.add(new CompanyDto.Slot(time, company.isActive() && !past && remaining > 0, remaining));
        }
        return slots;
    }

    /**
     * Comprueba que la hora es una franja valida de la empresa y que queda hueco.
     * @param excludeOrderId pedido que se esta editando (no cuenta para el aforo), o -1
     */
    void validateSlot(Company company, LocalDate date, LocalTime time, Long excludeOrderId) {
        if (!company.isActive()) {
            throw new BusinessException(company.getName() + " no acepta pedidos ahora mismo");
        }
        if (!slotTimes(company).contains(time)) {
            throw new BusinessException("La hora " + time + " no es una franja de recogida de " + company.getName());
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now())) {
            throw new BusinessException("La franja elegida ya ha pasado");
        }
        if (orderRepository.countInSlot(company, date, time, excludeOrderId) >= company.getOrdersPerSlot()) {
            throw new BusinessException("La franja de las " + time + " esta completa");
        }
    }

    private List<LocalTime> slotTimes(Company company) {
        List<LocalTime> times = new ArrayList<>();
        int close = company.getClosingTime().toSecondOfDay() / 60;
        for (int minute = company.getOpeningTime().toSecondOfDay() / 60; minute < close; minute += company.getSlotMinutes()) {
            times.add(LocalTime.of(minute / 60, minute % 60));
        }
        return times;
    }

    Company getEntity(Long id) {
        return companyRepository.findById(id).orElseThrow(() -> new NotFoundException("Empresa", id));
    }

    private void apply(Company company, CompanyDto.Request request) {
        if (!request.openingTime().isBefore(request.closingTime())) {
            throw new BusinessException("La hora de apertura debe ser anterior a la de cierre");
        }
        company.setName(request.name().trim());
        company.setType(request.type());
        company.setTaxId(request.taxId().trim().toUpperCase());
        company.setEmail(request.email());
        company.setPhone(request.phone());
        company.setAddress(request.address().trim());
        company.setOpeningTime(request.openingTime());
        company.setClosingTime(request.closingTime());
        company.setSlotMinutes(request.slotMinutes());
        company.setOrdersPerSlot(request.ordersPerSlot());
        company.setActive(request.active());
        if (request.delivery() != null) {
            company.setDelivery(request.delivery());
        }
    }

    private CompanyDto.Response toResponse(Company company) {
        return CompanyDto.Response.from(company, userRepository.countByCompanyId(company.getId()),
                customerRepository.countByCompanyId(company.getId()));
    }
}

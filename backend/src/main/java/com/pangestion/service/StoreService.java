package com.pangestion.service;

import com.pangestion.dto.StoreDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Store;
import com.pangestion.repository.OrderRepository;
import com.pangestion.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreService {

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<StoreDto.Response> findAll(boolean onlyActive) {
        List<Store> stores = onlyActive
                ? storeRepository.findByActiveTrueOrderByNameAsc()
                : storeRepository.findAllByOrderByNameAsc();
        return stores.stream().map(StoreDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public StoreDto.Response findById(Long id) {
        return StoreDto.Response.from(getEntity(id));
    }

    public StoreDto.Response create(StoreDto.Request request) {
        Store store = new Store();
        apply(store, request);
        return StoreDto.Response.from(storeRepository.save(store));
    }

    public StoreDto.Response update(Long id, StoreDto.Request request) {
        Store store = getEntity(id);
        apply(store, request);
        return StoreDto.Response.from(store);
    }

    public void delete(Long id) {
        Store store = getEntity(id);
        if (orderRepository.existsByStoreId(id)) {
            throw new BusinessException("La tienda tiene pedidos: desactivala en lugar de eliminarla");
        }
        storeRepository.delete(store);
    }

    /** Franjas de recogida de un dia, indicando si quedan huecos. Las pasadas o llenas no estan disponibles. */
    @Transactional(readOnly = true)
    public List<StoreDto.Slot> getSlots(Long storeId, LocalDate date) {
        Store store = getEntity(storeId);
        LocalDateTime now = LocalDateTime.now();
        List<StoreDto.Slot> slots = new ArrayList<>();
        for (LocalTime time : slotTimes(store)) {
            long booked = orderRepository.countInSlot(store, date, time, -1L);
            int remaining = (int) Math.max(0, store.getOrdersPerSlot() - booked);
            boolean past = LocalDateTime.of(date, time).isBefore(now);
            slots.add(new StoreDto.Slot(time, store.isActive() && !past && remaining > 0, remaining));
        }
        return slots;
    }

    /**
     * Comprueba que la hora es una franja valida de la tienda y que queda hueco.
     * @param excludeOrderId pedido que se esta editando (no cuenta para el aforo), o -1
     * @param checkPast      si true, rechaza franjas que ya han pasado
     */
    void validateSlot(Store store, LocalDate date, LocalTime time, Long excludeOrderId, boolean checkPast) {
        if (!store.isActive()) {
            throw new BusinessException("La tienda " + store.getName() + " no acepta pedidos");
        }
        if (!slotTimes(store).contains(time)) {
            throw new BusinessException("La hora " + time + " no es una franja de recogida de " + store.getName());
        }
        if (checkPast && LocalDateTime.of(date, time).isBefore(LocalDateTime.now())) {
            throw new BusinessException("La franja elegida ya ha pasado");
        }
        if (orderRepository.countInSlot(store, date, time, excludeOrderId) >= store.getOrdersPerSlot()) {
            throw new BusinessException("La franja de las " + time + " esta completa");
        }
    }

    private List<LocalTime> slotTimes(Store store) {
        List<LocalTime> times = new ArrayList<>();
        int close = store.getClosingTime().toSecondOfDay() / 60;
        for (int minute = store.getOpeningTime().toSecondOfDay() / 60; minute < close; minute += store.getSlotMinutes()) {
            times.add(LocalTime.of(minute / 60, minute % 60));
        }
        return times;
    }

    Store getEntity(Long id) {
        return storeRepository.findById(id).orElseThrow(() -> new NotFoundException("Tienda", id));
    }

    private void apply(Store store, StoreDto.Request request) {
        if (!request.openingTime().isBefore(request.closingTime())) {
            throw new BusinessException("La hora de apertura debe ser anterior a la de cierre");
        }
        store.setName(request.name().trim());
        store.setAddress(request.address().trim());
        store.setPhone(request.phone());
        store.setOpeningTime(request.openingTime());
        store.setClosingTime(request.closingTime());
        store.setSlotMinutes(request.slotMinutes());
        store.setOrdersPerSlot(request.ordersPerSlot());
        store.setActive(request.active());
    }
}

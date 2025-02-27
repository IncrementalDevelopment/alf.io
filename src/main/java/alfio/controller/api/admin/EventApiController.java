/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller.api.admin;

import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
import alfio.manager.TicketReservationManager;
import alfio.model.*;
import alfio.manager.i18n.I18nManager;
import alfio.manager.support.OrderSummary;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.TicketCategoryDescriptionRepository;
import alfio.repository.TicketFieldRepository;
import alfio.util.ValidationResult;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.util.OptionalWrapper.optionally;
import static alfio.util.Validator.validateCategory;
import static alfio.util.Validator.validateEventHeader;
import static alfio.util.Validator.validateEventPrices;
import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/admin/api")
@Log4j2
public class EventApiController {

    private static final String OK = "OK";
    private final EventManager eventManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final I18nManager i18nManager;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketFieldRepository ticketFieldRepository;

    @Autowired
    public EventApiController(EventManager eventManager,
                              EventStatisticsManager eventStatisticsManager,
                              I18nManager i18nManager,
                              TicketReservationManager ticketReservationManager,
                              TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository,
                              TicketFieldRepository ticketFieldRepository) {
        this.eventManager = eventManager;
        this.eventStatisticsManager = eventStatisticsManager;
        this.i18nManager = i18nManager;
        this.ticketReservationManager = ticketReservationManager;
        this.ticketCategoryDescriptionRepository = ticketCategoryDescriptionRepository;
        this.ticketFieldRepository = ticketFieldRepository;
    }

    @ExceptionHandler(DataAccessException.class)
    public String exception(DataAccessException e) {
        log.warn("unhandled exception", e);
        return "unexpected error. More info in the application log";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
        if(!IllegalArgumentException.class.isInstance(e)) {
            log.warn("unhandled exception", e);
        }
        return e.getMessage();
    }


    @RequestMapping(value = "/paymentProxies", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentProxy> getPaymentProxies() {
        return PaymentProxy.availableProxies();
    }

    @RequestMapping(value = "/events", method = GET)
    public List<EventWithStatistics> getAllEvents(Principal principal) {
        return eventStatisticsManager.getAllEventsWithStatistics(principal.getName()).stream()
                .sorted().collect(Collectors.toList());
    }

    @RequestMapping(value = "/events/{name}", method = GET)
    public Map<String, Object> getSingleEvent(@PathVariable("name") String eventName, Principal principal) {
        Map<String, Object> out = new HashMap<>();
        final String username = principal.getName();
        final EventWithStatistics event = eventStatisticsManager.getSingleEventWithStatistics(eventName, username);
        out.put("event", event);
        out.put("organization", eventManager.loadOrganizer(event.getEvent(), username));
        return out;
    }

    @RequestMapping(value = "/events/id/{eventId}", method = GET)
    public Event getSingleEventById(@PathVariable("eventId") int eventId, Principal principal) {
        return eventManager.getSingleEventById(eventId, principal.getName());
    }

    @RequestMapping(value = "/events/check", method = POST)
    public ValidationResult validateEvent(@RequestBody EventModification eventModification, Errors errors) {
        ValidationResult base = validateEventHeader(Optional.<Event>empty(), eventModification, errors)
            .or(validateEventPrices(Optional.<Event>empty(), eventModification, errors));
        AtomicInteger counter = new AtomicInteger();
        return base.or(eventModification.getTicketCategories().stream()
            .map(c -> validateCategory(c, errors, "ticketCategories[" + counter.getAndIncrement() + "]."))
            .reduce(ValidationResult::or)
            .orElse(ValidationResult.success()));
    }

    @RequestMapping(value = "/events/new", method = POST)
    public String insertEvent(@RequestBody EventModification eventModification) {
        eventManager.createEvent(eventModification);
        return OK;
    }

    @RequestMapping(value = "/events/{id}/header/update", method = POST)
    public ValidationResult updateHeader(@PathVariable("id") int id, @RequestBody EventModification eventModification, Errors errors,  Principal principal) {
        Event event = eventManager.getSingleEventById(id, principal.getName());
        return validateEventHeader(Optional.of(event), eventModification, errors).ifSuccess(() -> eventManager.updateEventHeader(event, eventModification, principal.getName()));
    }

    @RequestMapping(value = "/events/{id}/prices/update", method = POST)
    public ValidationResult updatePrices(@PathVariable("id") int id, @RequestBody EventModification eventModification, Errors errors,  Principal principal) {
        Event event = eventManager.getSingleEventById(id, principal.getName());
        return validateEventPrices(Optional.of(event), eventModification, errors).ifSuccess(() -> eventManager.updateEventPrices(event, eventModification, principal.getName()));
    }

    @RequestMapping(value = "/events/{eventId}/categories/{categoryId}/update", method = POST)
    public ValidationResult updateExistingCategory(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, @RequestBody TicketCategoryModification category, Errors errors, Principal principal) {
        return validateCategory(category, errors).ifSuccess(() -> eventManager.updateCategory(categoryId, eventId, category, principal.getName()));
    }

    @RequestMapping(value = "/events/{eventId}/categories/new", method = POST)
    public ValidationResult createCategory(@PathVariable("eventId") int eventId, @RequestBody TicketCategoryModification category, Errors errors, Principal principal) {
        return validateCategory(category, errors).ifSuccess(() -> eventManager.insertCategory(eventId, category, principal.getName()));
    }

    @RequestMapping(value = "/events/reallocate", method = PUT)
    public String reallocateTickets(@RequestBody TicketAllocationModification form) {
        eventManager.reallocateTickets(form.getSrcCategoryId(), form.getTargetCategoryId(), form.getEventId());
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/category/{categoryId}/unbind-tickets", method = PUT)
    public String unbindTickets(@PathVariable("eventName") String eventName, @PathVariable("categoryId") int categoryId, Principal principal) {
        eventManager.unbindTickets(eventName, categoryId, principal.getName());
        return OK;
    }

    private static final List<String> FIXED_FIELDS = Arrays.asList("ID", "creation", "category", "event", "status", "originalPrice", "paidPrice","reservationID", "Name", "E-Mail", "locked", "Language");
    private static final int[] BOM_MARKERS = new int[] {0xEF, 0xBB, 0xBF};

    @RequestMapping("/events/{eventName}/export.csv")
    public void downloadAllTicketsCSV(@PathVariable("eventName") String eventName, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        List<String> fields = Arrays.asList(Optional.ofNullable(request.getParameterValues("fields")).orElse(new String[] {}));
        Event event = Optional.ofNullable(eventManager.getSingleEvent(eventName, principal.getName())).orElseThrow(IllegalArgumentException::new);
        Map<Integer, TicketCategory> categoriesMap = eventManager.loadTicketCategories(event).stream().collect(Collectors.toMap(TicketCategory::getId, Function.identity()));
        ZoneId eventZoneId = event.getZoneId();

        Predicate<String> contains = FIXED_FIELDS::contains;

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-export.csv");

        try(ServletOutputStream out = response.getOutputStream()) {

            for (int marker : BOM_MARKERS) {//UGLY-MODE_ON: specify that the file is written in UTF-8 with BOM, thanks to alexr http://stackoverflow.com/a/4192897
                out.write(marker);
            }
            CSVWriter writer = new CSVWriter(new OutputStreamWriter(out));
            writer.writeNext(fields.toArray(new String[]{}));

            eventManager.findAllConfirmedTickets(eventName, principal.getName()).stream().map(t -> {
                List<String> line = new ArrayList<>();
                if(fields.contains("ID")) {line.add(t.getUuid());}
                if(fields.contains("creation")) {line.add(t.getCreation().withZoneSameInstant(eventZoneId).toString());}
                if(fields.contains("category")) {line.add(categoriesMap.get(t.getCategoryId()).getName());}
                if(fields.contains("event")) {line.add(eventName);}
                if(fields.contains("status")) {line.add(t.getStatus().toString());}
                if(fields.contains("originalPrice")) {line.add(t.getOriginalPrice().toString());}
                if(fields.contains("paidPrice")) {line.add(t.getPaidPrice().toString());}
                if(fields.contains("reservationID")) {line.add(t.getTicketsReservationId());}
                if(fields.contains("Name")) {line.add(t.getFullName());}
                if(fields.contains("E-Mail")) {line.add(t.getEmail());}
                if(fields.contains("locked")) {line.add(String.valueOf(t.getLockedAssignment()));}
                if(fields.contains("Language")) {line.add(String.valueOf(t.getUserLanguage()));}

                //obviously not optimized
                Map<String, String> additionalValues = ticketFieldRepository.findAllValuesForTicketId(t.getId());

                fields.stream().filter(contains.negate()).forEachOrdered(field -> {
                    line.add(additionalValues.getOrDefault(field, ""));
                });

                return line.toArray(new String[]{});
            }).forEachOrdered(writer::writeNext);
            writer.flush();
            out.flush();
        }


    }

    @RequestMapping("/events/{eventName}/fields")
    public List<String> getAllFields(@PathVariable("eventName") String eventName) {
        List<String> fields = new ArrayList<>();
        fields.addAll(FIXED_FIELDS);
        fields.addAll(ticketFieldRepository.findFieldsForEvent(eventName));
        return fields;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments")
    public List<SerializablePair<TicketReservation, OrderSummary>> getPendingPayments(@PathVariable("eventName") String eventName, Principal principal) {
        return ticketReservationManager.getPendingPayments(eventStatisticsManager.getSingleEventWithStatistics(eventName, principal.getName())).stream()
                .map(SerializablePair::fromPair).collect(Collectors.toList());
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/{reservationId}/confirm", method = POST)
    public String confirmPayment(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        ticketReservationManager.confirmOfflinePayment(loadEvent(eventName, principal), reservationId);
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/{reservationId}", method = DELETE)
    public String deletePendingPayment(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        ticketReservationManager.deleteOfflinePayment(loadEvent(eventName, principal), reservationId);
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/bulk-confirmation", method = POST)
    public List<Triple<Boolean, String, String>> bulkConfirmation(@PathVariable("eventName") String eventName,
                                                                  Principal principal,
                                                                  @RequestBody UploadBase64FileModification file) throws IOException {

        try(InputStreamReader isr = new InputStreamReader(file.getInputStream())) {
            CSVReader reader = new CSVReader(isr);
            Event event = loadEvent(eventName, principal);
            return reader.readAll().stream()
                    .map(line -> {
                        String reservationID = null;
                        try {
                            Validate.isTrue(line.length >= 2);
                            reservationID = line[0];
                            ticketReservationManager.validateAndConfirmOfflinePayment(reservationID, event, new BigDecimal(line[1]));
                            return Triple.of(Boolean.TRUE, reservationID, "");
                        } catch (Exception e) {
                            return Triple.of(Boolean.FALSE, Optional.ofNullable(reservationID).orElse(""), e.getMessage());
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    @RequestMapping(value = "/events/{eventName}/categories/{categoryId}/tickets/{ticketId}/toggle-locking", method = PUT)
    public boolean toggleTicketLocking(@PathVariable("eventName") String eventName,
                                       @PathVariable("categoryId") int categoryId,
                                       @PathVariable("ticketId") int ticketId,
                                       Principal principal) {
        return eventManager.toggleTicketLocking(eventName, categoryId, ticketId, principal.getName());
    }

    @RequestMapping(value = "/events/{eventName}/languages", method = GET)
    public List<ContentLanguage> getAvailableLocales(@PathVariable("eventName") String eventName) {
        return i18nManager.getEventLanguages(eventName);
    }

    @RequestMapping(value = "/events-all-languages", method = GET)
    public List<ContentLanguage> getAllLanguages() {
        return i18nManager.getAvailableLanguages();
    }

    @RequestMapping(value = "/events-supported-languages", method = GET)
    public List<ContentLanguage> getSupportedLanguages() {
        return i18nManager.getSupportedLanguages();
    }

    @RequestMapping(value = "/events/{eventName}/categories-containing-tickets", method = GET)
    public List<TicketCategoryModification> getCategoriesWithTickets(@PathVariable("eventName") String eventName, Principal principal) {
        Event event = loadEvent(eventName, principal);
        return eventStatisticsManager.loadTicketCategoriesWithStats(event).stream()
                .filter(tc -> !tc.getTickets().isEmpty())
                .map(tc -> TicketCategoryModification.fromTicketCategory(tc.getTicketCategory(), ticketCategoryDescriptionRepository.findByTicketCategoryId(tc.getId()), event.getZoneId()))
                .collect(Collectors.toList());
    }

    private Event loadEvent(String eventName, Principal principal) {
        Optional<Event> singleEvent = optionally(() -> eventManager.getSingleEvent(eventName, principal.getName()));
        Validate.isTrue(singleEvent.isPresent(), "event not found");
        return singleEvent.get();
    }

}

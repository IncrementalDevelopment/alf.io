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
package alfio.manager;

import alfio.manager.plugin.PluginManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketCategoryWithStatistic;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.EventUtil;
import alfio.util.PreReservedTicketDistributor;
import alfio.util.TemplateManager;
import alfio.util.WorkingDaysAdjusters;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.EventUtil.determineAvailableSeats;

@Component
@Log4j2
public class WaitingQueueManager {

    private final WaitingQueueRepository waitingQueueRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ConfigurationManager configurationManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationManager notificationManager;
    private final TemplateManager templateManager;
    private final MessageSource messageSource;
    private final OrganizationRepository organizationRepository;
    private final PluginManager pluginManager;

    @Autowired
    public WaitingQueueManager(WaitingQueueRepository waitingQueueRepository,
                               TicketRepository ticketRepository,
                               TicketCategoryRepository ticketCategoryRepository,
                               ConfigurationManager configurationManager,
                               EventStatisticsManager eventStatisticsManager,
                               NamedParameterJdbcTemplate jdbc,
                               NotificationManager notificationManager,
                               TemplateManager templateManager,
                               MessageSource messageSource,
                               OrganizationRepository organizationRepository,
                               PluginManager pluginManager) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.ticketRepository = ticketRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.configurationManager = configurationManager;
        this.eventStatisticsManager = eventStatisticsManager;
        this.notificationManager = notificationManager;
        this.jdbc = jdbc;
        this.templateManager = templateManager;
        this.messageSource = messageSource;
        this.organizationRepository = organizationRepository;
        this.pluginManager = pluginManager;
    }

    public boolean subscribe(Event event, String fullName, String email, Locale userLanguage) {
        try {
            WaitingQueueSubscription.Type subscriptionType = getSubscriptionType(event);
            validateSubscriptionType(event, subscriptionType);
            AffectedRowCountAndKey<Integer> key = waitingQueueRepository.insert(event.getId(), fullName, email, ZonedDateTime.now(event.getZoneId()), userLanguage.getLanguage(), subscriptionType);
            notifySubscription(event, fullName, email, userLanguage, subscriptionType);
            pluginManager.handleWaitingQueueSubscription(waitingQueueRepository.loadById(key.getKey()));
            return true;
        } catch(DuplicateKeyException e) {
            return true;//why are you subscribing twice?
        } catch (Exception e) {
            log.error("error during subscription", e);
            return false;
        }
    }

    private void notifySubscription(Event event, String fullName, String email, Locale userLanguage, WaitingQueueSubscription.Type subscriptionType) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("fullName", fullName);
        model.put("organization", organization);
        notificationManager.sendSimpleEmail(event, email, messageSource.getMessage("email-waiting-queue.subscribed.subject", new Object[]{event.getDisplayName()}, userLanguage),
                () -> templateManager.renderClassPathResource("/alfio/templates/waiting-queue-joined.ms", model, userLanguage, TemplateManager.TemplateOutput.TEXT));
        if(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE_NOTIFICATION), false)) {
            String adminTemplate = messageSource.getMessage("email-waiting-queue.subscribed.admin.text",
                    new Object[] {subscriptionType, event.getDisplayName()}, Locale.ENGLISH);
            notificationManager.sendSimpleEmail(event, organization.getEmail(), messageSource.getMessage("email-waiting-queue.subscribed.admin.subject",
                            new Object[]{event.getDisplayName()}, Locale.ENGLISH),
                    () -> templateManager.renderString(adminTemplate, model, Locale.ENGLISH, TemplateManager.TemplateOutput.TEXT));
        }

    }

    private WaitingQueueSubscription.Type getSubscriptionType(Event event) {
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        return eventStatisticsManager.loadTicketCategories(event).stream()
                .findFirst()
                .filter(tc -> now.isBefore(tc.getInception(event.getZoneId())))
                .map(tc -> WaitingQueueSubscription.Type.PRE_SALES)
                .orElse(WaitingQueueSubscription.Type.SOLD_OUT);
    }

    private void validateSubscriptionType(Event event, WaitingQueueSubscription.Type type) {
        if(type == WaitingQueueSubscription.Type.PRE_SALES) {
            Validate.isTrue(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false), "PRE_SALES Waiting queue is not active");
        } else {
            Validate.isTrue(eventStatisticsManager.noSeatsAvailable().test(event), "SOLD_OUT Waiting queue is not active");
        }
    }

    public List<WaitingQueueSubscription> loadAllSubscriptionsForEvent(int eventId) {
        return waitingQueueRepository.loadAllWaiting(eventId);
    }

    public int countSubscribers(int eventId) {
        return waitingQueueRepository.countWaitingPeople(eventId);
    }

    Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeSeats(Event event) {
        int eventId = event.getId();
        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAllWaiting(eventId);
        int waitingPeople = subscriptions.size();
        int waitingTickets = ticketRepository.countWaiting(eventId);
        if (waitingPeople == 0 && waitingTickets > 0) {
            ticketRepository.revertToFree(eventId);
        } else if (waitingPeople > 0 && waitingTickets > 0) {
            return distributeAvailableSeats(event, waitingPeople, waitingTickets);
        } else if(subscriptions.stream().anyMatch(WaitingQueueSubscription::isPreSales) && configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false)) {
            return handlePreReservation(event, waitingPeople, waitingTickets);
        }
        return Stream.empty();
    }

    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> handlePreReservation(Event event, int waitingPeople, int waitingTickets) {
        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        // Given that this Job runs more than once in a minute, in order to ensure that all the waiting queue subscribers would get a seat *before*
        // all other people, we must process their a little bit before the sale period starts
        Optional<TicketCategory> categoryWithInceptionInFuture = ticketCategories.stream()
                .sorted(TicketCategory.COMPARATOR)
                .findFirst()
                .filter(t -> ZonedDateTime.now(event.getZoneId()).isBefore(t.getInception(event.getZoneId()).minusMinutes(5)));
        int ticketsNeeded = Math.min(waitingPeople, event.getAvailableSeats());
        if(ticketsNeeded > 0) {
            preReserveIfNeeded(event, ticketsNeeded);
            if(!categoryWithInceptionInFuture.isPresent()) {
                return distributeAvailableSeats(event, Ticket.TicketStatus.PRE_RESERVED, () -> ticketsNeeded);
            }
        }
        return Stream.empty();
    }

    private void preReserveIfNeeded(Event event, int ticketsNeeded) {
        int eventId = event.getId();
        int alreadyReserved = ticketRepository.countPreReservedTickets(eventId);
        if(alreadyReserved < ticketsNeeded) {
            preReserveTickets(event, ticketsNeeded, eventId, alreadyReserved);
        }
    }

    private void preReserveTickets(Event event, int ticketsNeeded, int eventId, int alreadyReserved) {
        final int toBeGenerated = Math.abs(alreadyReserved - ticketsNeeded);
        EventWithStatistics eventWithStatistics = eventStatisticsManager.fillWithStatistics(event);
        List<Pair<Integer, Integer>> collectedTickets = eventWithStatistics.getTicketCategories().stream()
                .filter(tc -> !tc.isAccessRestricted())
                .map(tc -> Pair.of(determineAvailableSeats(tc, eventWithStatistics), tc))
                .collect(new PreReservedTicketDistributor(toBeGenerated));
        MapSqlParameterSource[] candidates = collectedTickets.stream()
                .flatMap(p -> ticketRepository.selectFreeTicketsForPreReservation(eventId, p.getKey()).stream())
                .map(id -> new MapSqlParameterSource().addValue("id", id))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(ticketRepository.preReserveTicket(), candidates);
    }


    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeAvailableSeats(Event event, int waitingPeople, int waitingTickets) {
        return distributeAvailableSeats(event, Ticket.TicketStatus.RELEASED, () -> Math.min(waitingPeople, waitingTickets));
    }

    private Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> distributeAvailableSeats(Event event, Ticket.TicketStatus status, Supplier<Integer> availableSeatSupplier) {
        int availableSeats = availableSeatSupplier.get();
        int eventId = event.getId();
        log.debug("processing {} subscribers from waiting queue", availableSeats);
        Iterator<Ticket> tickets = ticketRepository.selectWaitingTicketsForUpdate(eventId, status.name(), availableSeats).iterator();
        Optional<TicketCategory> unboundedCategory = ticketCategoryRepository.findUnboundedOrderByExpirationDesc(eventId).stream().findFirst();
        int expirationTimeout = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), WAITING_QUEUE_RESERVATION_TIMEOUT), 4);
        ZonedDateTime expiration = ZonedDateTime.now(event.getZoneId()).plusHours(expirationTimeout).with(WorkingDaysAdjusters.defaultWorkingDays());

        if(!tickets.hasNext()) {
            log.warn("Unable to assign tickets, returning an empty stream");
            return Stream.empty();
        }
        return waitingQueueRepository.loadWaiting(eventId, availableSeats).stream()
            .map(wq -> Pair.of(wq, tickets.next()))
            .map(pair -> {
                TicketReservationModification ticketReservation = new TicketReservationModification();
                ticketReservation.setAmount(1);
                Integer categoryId = Optional.ofNullable(pair.getValue().getCategoryId()).orElseGet(() -> unboundedCategory.orElseThrow(RuntimeException::new).getId());
                ticketReservation.setTicketCategoryId(categoryId);
                return Pair.of(pair.getLeft(), new TicketReservationWithOptionalCodeModification(ticketReservation, Optional.<SpecialPrice>empty()));
            })
            .map(pair -> Triple.of(pair.getKey(), pair.getValue(), expiration));
    }

    public void fireReservationConfirmed(String reservationId) {
        updateStatus(reservationId, WaitingQueueSubscription.Status.ACQUIRED.toString());
    }

    public void fireReservationExpired(String reservationId) {
        waitingQueueRepository.bulkUpdateExpiredReservations(Collections.singletonList(reservationId));
    }

    public void cleanExpiredReservations(List<String> reservationIds) {
        waitingQueueRepository.bulkUpdateExpiredReservations(reservationIds);
    }

    private void updateStatus(String reservationId, String status) {
        waitingQueueRepository.updateStatusByReservationId(reservationId, status);
    }
}

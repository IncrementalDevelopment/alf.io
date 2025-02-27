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
package alfio.model;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import alfio.model.transaction.PaymentProxy;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.time.ZonedDateTime;
import java.util.Date;

@Getter
public class FullTicketInfo {

    @Delegate
    private final Ticket ticket;

    private final TicketReservation ticketReservation;

    private final TicketCategory ticketCategory;


    public FullTicketInfo(@Column("t_id") int id,
                          @Column("t_uuid") String uuid,
                          @Column("t_creation") ZonedDateTime creation,
                          @Column("t_category_id") int categoryId,
                          @Column("t_status") String status,
                          @Column("t_event_id") int eventId,
                          @Column("t_original_price_cts") int originalPriceInCents,
                          @Column("t_paid_price_cts") int paidPriceInCents,
                          @Column("t_tickets_reservation_id") String ticketsReservationId,
                          @Column("t_full_name") String fullName,
                          @Column("t_email_address") String email,
                          @Column("t_locked_assignment") boolean lockedAssignment,
                          //
                          @Column("t_user_language") String userLanguage,
                          //
                          @Column("tr_id") String trId,
                          @Column("tr_validity") Date trValidity,
                          @Column("tr_status") TicketReservation.TicketReservationStatus trStatus,
                          @Column("tr_full_name") String trFullName,
                          @Column("tr_email_address") String trEmail,
                          @Column("tr_billing_address") String trBillingAddress,
                          @Column("tr_confirmation_ts") ZonedDateTime trConfirmationTimestamp,
                          @Column("tr_latest_reminder_ts") ZonedDateTime trLatestReminder,
                          @Column("tr_payment_method") PaymentProxy trPaymentMethod,
                          @Column("tr_offline_payment_reminder_sent") Boolean trReminderSent,
                          @Column("tr_promo_code_id_fk") Integer trPromoCodeDiscountId,
                          @Column("tr_automatic") boolean trAutomatic,
                          @Column("tr_user_language") String resUserLanguage,
                          //
                          //
                          @Column("tc_id") int tcId,
                          @Column("tc_inception") ZonedDateTime tcUtcInception,
                          @Column("tc_expiration") ZonedDateTime tcUtcExpiration,
                          @Column("tc_max_tickets") int tcMaxTickets,
                          @Column("tc_name") String tcName,
                          @Column("tc_price_cts") int tcPriceInCents,
                          @Column("tc_access_restricted") boolean tcAccessRestricted,
                          @Column("tc_tc_status") TicketCategory.Status tcStatus,
                          @Column("tc_event_id") int tcEventId,
                          @Column("tc_bounded") boolean bounded) {

        this.ticket = new Ticket(id, uuid, creation, categoryId, status, eventId, originalPriceInCents, paidPriceInCents,
                ticketsReservationId, fullName, email, lockedAssignment, userLanguage);
        this.ticketReservation = new TicketReservation(trId, trValidity, trStatus, trFullName, trEmail, trBillingAddress,
                trConfirmationTimestamp, trLatestReminder, trPaymentMethod, trReminderSent, trPromoCodeDiscountId, trAutomatic, resUserLanguage);
        this.ticketCategory = new TicketCategory(tcId, tcUtcInception, tcUtcExpiration, tcMaxTickets, tcName,
                tcPriceInCents, tcAccessRestricted, tcStatus, tcEventId, bounded);

    }
}

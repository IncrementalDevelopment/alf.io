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
package alfio.controller.support;

import alfio.model.Ticket;
import lombok.experimental.Delegate;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TicketDecorator {

    @Delegate
    private final Ticket ticket;
    private final boolean freeCancellationEnabled;
    private final boolean conditionsMet;
    private final String urlSuffix;

    public TicketDecorator(Ticket ticket, boolean freeCancellationEnabled, boolean conditionsMet) {
        this(ticket, freeCancellationEnabled, conditionsMet, ticket.getUuid());
    }

    public TicketDecorator(Ticket ticket, boolean freeCancellationEnabled, boolean conditionsMet, String urlSuffix) {
        this.ticket = ticket;
        this.freeCancellationEnabled = freeCancellationEnabled;
        this.conditionsMet = conditionsMet;
        this.urlSuffix = urlSuffix;
    }

    public String getUrlSuffix() {
        return urlSuffix;
    }

    public boolean hasBeenPaid() {
        return !isFree();
    }

    public boolean isFree() {
        return getPaidPriceInCents() == 0;
    }

    public boolean getCancellationEnabled() {
        return isFree() && freeCancellationEnabled && conditionsMet;
    }

    public static List<TicketDecorator> decorate(List<Ticket> tickets, boolean freeCancellationEnabled, Function<Ticket, Boolean> categoryEvaluator) {
        return tickets.stream().map(t -> new TicketDecorator(t, freeCancellationEnabled, categoryEvaluator.apply(t))).collect(Collectors.toList());
    }
}

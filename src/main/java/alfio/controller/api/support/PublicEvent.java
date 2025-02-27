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
package alfio.controller.api.support;

import alfio.model.Event;
import alfio.model.EventDescription;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class PublicEvent extends EventListItem {

    private final List<PublicCategory> categories;

    public PublicEvent(Event event, String requestContextPath, DataLoader<Event, EventDescription> eventDescriptionsLoader, List<PublicCategory> categories) {
        super(event, requestContextPath, eventDescriptionsLoader);
        this.categories = categories;
    }

    public List<PublicCategory> getActiveCategories() {
        return categories.stream()
            .filter(c -> !c.isAccessRestricted() && c.isActive())
            .sorted(PublicCategory.SORT_BY_DATE)
            .collect(toList());
    }

    public List<PublicCategory> getExpiredCategories() {
        return categories.stream()
            .filter(c -> !c.isAccessRestricted() && !c.isActive())
            .sorted(PublicCategory.SORT_BY_DATE)
            .collect(toList());
    }

    public String getTimeZone() {
        return event.getTimeZone();
    }


}

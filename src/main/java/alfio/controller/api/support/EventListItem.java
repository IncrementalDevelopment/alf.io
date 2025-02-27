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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class EventListItem {

    protected final Event event;
    protected final String requestContextPath;
    protected final List<EventDescription> eventDescriptions;

    public EventListItem(Event event, String requestContextPath, DataLoader<Event, EventDescription> eventDescriptionsLoader) {
        this.event = event;
        this.requestContextPath = requestContextPath;
        this.eventDescriptions = eventDescriptionsLoader.load(event);
    }

    public String getImageUrl() {
        return event.getFileBlobIdIsPresent() ? requestContextPath + "/file/" + event.getFileBlobId() : event.getImageUrl();
    }

    public String getUrl() {
        return event.isInternal() ? requestContextPath + "/events/" + event.getShortName() : event.getExternalUrl();
    }

    public boolean isExternal() {
        return !event.isInternal();
    }

    public String getName() {
        return event.getDisplayName();
    }

    public List<PublicEventDescription> getDescriptions() {
        return eventDescriptions.stream().map(PublicEventDescription::fromEventDescription).collect(Collectors.toList());
    }

    public boolean isSameDay() {
        return event.getSameDay();
    }

    public ZonedDateTime getBegin() {
        return event.getBegin();
    }

    public ZonedDateTime getEnd() {
        return event.getEnd();
    }

    public String getLocation() {
        return event.getLocation();
    }

    public String getLatitude() {
        return event.getLatitude();
    }

    public String getLongitude() {
        return event.getLongitude();
    }

}

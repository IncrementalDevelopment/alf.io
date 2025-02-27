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
package alfio.repository;

import alfio.model.EmailMessage;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@QueryRepository
public interface EmailMessageRepository {

    /**
     * This method returns a lightweight instance of EmailMessage. The property "Attachments" is always null.
     * @param eventId
     * @param checksum
     * @return
     */
    @Query("select id, event_id, status, recipient, subject, message, null as attachments, checksum, request_ts, sent_ts from email_message where event_id = :eventId and checksum = :checksum limit 1")
    Optional<EmailMessage> findByEventIdAndChecksum(@Bind("eventId") int eventId, @Bind("checksum") String checksum);

    @Query("insert into email_message (event_id, status, recipient, subject, message, attachments, checksum, request_ts) values(:eventId, 'WAITING', :recipient, :subject, :message, :attachments, :checksum, :timestamp)")
    int insert(@Bind("eventId") int eventId,
               @Bind("recipient") String recipient,
               @Bind("subject") String subject,
               @Bind("message") String message,
               @Bind("attachments") String attachments,
               @Bind("checksum") String checksum,
               @Bind("timestamp") ZonedDateTime requestTimestamp);


    @Query("update email_message set status = :status where event_id = :eventId and checksum = :checksum and status in (:expectedStatuses)")
    int updateStatus(@Bind("eventId") int eventId, @Bind("checksum") String checksum, @Bind("status") String status, @Bind("expectedStatuses") List<String> expectedStatuses);

    @Query("update email_message set status = :status where id = :messageId and event_id = :eventId")
    int updateStatus(@Bind("eventId") int eventId, @Bind("status") String status, @Bind("messageId") int messageId);

    @Query("update email_message set status = 'RETRY', request_ts = :requestTs where status in ('WAITING', 'ERROR', 'RETRY') and request_ts > :expiration")
    int updateStatusForRetry(@Bind("requestTs") ZonedDateTime now, @Bind("expiration") ZonedDateTime expiration);

    @Query("select id, event_id, status, recipient, subject, message, attachments, checksum, request_ts, sent_ts from email_message where event_id = :eventId and status = 'WAITING' for update")
    List<EmailMessage> loadWaitingForProcessing(@Bind("eventId") int eventId);

    @Query("select id, event_id, status, recipient, subject, message, attachments, checksum, request_ts, sent_ts from email_message where event_id = :eventId and status = 'RETRY' for update")
    List<EmailMessage> loadRetryForProcessing(@Bind("eventId") int eventId);

    @Query("update email_message set status = 'SENT', sent_ts = :sentTimestamp where event_id = :eventId and checksum = :checksum and status in (:expectedStatuses)")
    int updateStatusToSent(@Bind("eventId") int eventId, @Bind("checksum") String checksum, @Bind("sentTimestamp") ZonedDateTime sentTimestamp, @Bind("expectedStatuses") List<String> expectedStatuses);

    @Query("select * from email_message where event_id = :eventId")
    List<EmailMessage> findByEventId(@Bind("eventId") int eventId);

    @Query("select * from email_message where id = :messageId and event_id = :eventId")
    Optional<EmailMessage> findByEventIdAndMessageId(@Bind("eventId") int eventId, @Bind("messageId") int messageId);

}

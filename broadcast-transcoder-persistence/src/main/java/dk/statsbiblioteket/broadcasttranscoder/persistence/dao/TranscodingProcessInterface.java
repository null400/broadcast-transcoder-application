package dk.statsbiblioteket.broadcasttranscoder.persistence.dao;

import dk.statsbiblioteket.broadcasttranscoder.persistence.TranscodingStateEnum;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: abr
 * Date: 1/23/13
 * Time: 4:49 PM
 * To change this template use File | Settings | File Templates.
 */
public interface TranscodingProcessInterface<T> extends GenericDAO<T, String> {
    List<T> getAllTranscodings(long since, TranscodingStateEnum state);

    void markAsChangedInDoms(String programpid, long timestamp);

    void markAsAlreadyTranscoded(String programpid, long timestamp);

    void markAsFailed(String programpid, long timestamp, String message);

    void markAsRejected(String programpid, long timestamp, String message);

    long getLatestTranscodingTimestamp(String programPid);

    long getLatestChangeInDomsTimestamp(String programPid);
}

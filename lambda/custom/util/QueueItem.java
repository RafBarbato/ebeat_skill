package util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.sql.Timestamp;

/**
 * POJO per una riga di playback_queue (caso 7).
 * Ordinata per position; position=1 è la prossima dopo current_track.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueItem {

    String user_id;
    Integer position;
    String youtube_id;
    String url;
    Timestamp url_expires_at;
    Long track_id;
    String track_title;
    String track_artist;
    Long track_duration;
    Timestamp added_at;

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public String getYoutube_id() { return youtube_id; }
    public void setYoutube_id(String youtube_id) { this.youtube_id = youtube_id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Timestamp getUrl_expires_at() { return url_expires_at; }
    public void setUrl_expires_at(Timestamp url_expires_at) { this.url_expires_at = url_expires_at; }

    public Long getTrack_id() { return track_id; }
    public void setTrack_id(Long track_id) { this.track_id = track_id; }

    public String getTrack_title() { return track_title; }
    public void setTrack_title(String track_title) { this.track_title = track_title; }

    public String getTrack_artist() { return track_artist; }
    public void setTrack_artist(String track_artist) { this.track_artist = track_artist; }

    public Long getTrack_duration() { return track_duration; }
    public void setTrack_duration(Long track_duration) { this.track_duration = track_duration; }

    public Timestamp getAdded_at() { return added_at; }
    public void setAdded_at(Timestamp added_at) { this.added_at = added_at; }
}

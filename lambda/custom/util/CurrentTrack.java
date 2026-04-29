package util;

import java.sql.Timestamp;

public class CurrentTrack {

    Integer id;
    Timestamp created_at;
    Timestamp updated_at;
    String user_id;
    String url;
    Timestamp url_expires_at;
    Long offset;
    Long track_id;
    String track_title;
    String track_artist;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Timestamp getCreated_at() { return created_at; }
    public void setCreated_at(Timestamp created_at) { this.created_at = created_at; }

    public Timestamp getUpdated_at() { return updated_at; }
    public void setUpdated_at(Timestamp updated_at) { this.updated_at = updated_at; }

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Timestamp getUrl_expires_at() { return url_expires_at; }
    public void setUrl_expires_at(Timestamp url_expires_at) { this.url_expires_at = url_expires_at; }

    public Long getOffset() { return offset; }
    public void setOffset(Long offset) { this.offset = offset; }

    public Long getTrack_id() { return track_id; }
    public void setTrack_id(Long track_id) { this.track_id = track_id; }

    public String getTrack_title() { return track_title; }
    public void setTrack_title(String track_title) { this.track_title = track_title; }

    public String getTrack_artist() { return track_artist; }
    public void setTrack_artist(String track_artist) { this.track_artist = track_artist; }

    public boolean isExpired() {
        return url_expires_at != null
                && url_expires_at.before(new Timestamp(System.currentTimeMillis()));
    }
}

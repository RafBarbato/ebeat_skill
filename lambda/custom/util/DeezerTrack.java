package util;

/**
 * POJO per un risultato di ricerca Deezer (api.deezer.com/search/track).
 * Campi piatti, valorizzati dal {@code DeezerService} estraendo
 * {@code data[i].artist.name} dalla risposta JSON.
 */
public class DeezerTrack {

    private Long id;
    private String title;
    private String artist;
    private Long duration;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
}

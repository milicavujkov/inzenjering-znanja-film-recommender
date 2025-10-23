package cbr;

import ucm.gaia.jcolibri.cbrcore.Attribute;
import ucm.gaia.jcolibri.cbrcore.CaseComponent;

public class CaseDescription implements CaseComponent {
    private String id;
    private String title;
    private Integer year;
    private Double imdbRating;
    private String director;
    private String genres;
    private String actors;
    private String languages;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Double getImdbRating() { return imdbRating; }
    public void setImdbRating(Double imdbRating) { this.imdbRating = imdbRating; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getGenres() { return genres; }
    public void setGenres(String genres) { this.genres = genres; }

    public String getActors() { return actors; }
    public void setActors(String actors) { this.actors = actors; }

    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }

    @Override
    public Attribute getIdAttribute() {
        return new Attribute("id", this.getClass());
    }

    @Override
    public String toString() {
        return title + " (" + year + ") - " + director + " | " + genres + " | IMDb: " + imdbRating;
    }
}
package cbr;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;

import java.util.*;
import java.util.stream.Collectors;

public class CaseBasedReasoning {

    private Model model;

    public CaseBasedReasoning(Model model) {
        this.model = model;
    }

    public List<SimilarFilm> findSimilarFilms(String targetFilmTitle, int topN) {
        FilmMetadata target = getFilmMetadata(targetFilmTitle);

        if (target == null) {
            return Collections.emptyList();
        }

        List<FilmMetadata> allFilms = getAllFilms();

        List<SimilarFilm> similarities = new ArrayList<>();

        for (FilmMetadata candidate : allFilms) {
            // skips the target film
            if (candidate.title.equalsIgnoreCase(target.title)) {
                continue;
            }

            double score = calculateSimilarity(target, candidate);
            similarities.add(new SimilarFilm(candidate.title, score, candidate));
        }

        // sorts by score descending and returns topN
        return similarities.stream()
                .sorted(Comparator.comparingDouble(SimilarFilm::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    private double calculateSimilarity(FilmMetadata target, FilmMetadata candidate) {
        double score = 0.0;

        // 1. same genre: +30 points (per matching genre)
        for (String targetGenre : target.genres) {
            if (candidate.genres.contains(targetGenre)) {
                score += 30;
            }
        }

        // 2. same director: +25 points
        if (target.director != null && target.director.equals(candidate.director)) {
            score += 25;
        }

        // 3. common actors: +20 points per actor
        for (String targetActor : target.actors) {
            if (candidate.actors.contains(targetActor)) {
                score += 20;
            }
        }

        // 4. similar IMDb rating: +15 points if difference < 0.5
        if (Math.abs(target.imdbRating - candidate.imdbRating) < 0.5) {
            score += 15;
        }

        // 5. same decade: +10 points
        int targetDecade = (target.year / 10) * 10;
        int candidateDecade = (candidate.year / 10) * 10;
        if (targetDecade == candidateDecade) {
            score += 10;
        }

        // 6. same language: +5 points
        for (String targetLang : target.languages) {
            if (candidate.languages.contains(targetLang)) {
                score += 5;
            }
        }

        return score;
    }

    private FilmMetadata getFilmMetadata(String filmTitle) {
        String sparql =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?title ?year ?imdb ?director " +
                        "       (GROUP_CONCAT(DISTINCT ?genreName; separator=\",\") AS ?genres) " +
                        "       (GROUP_CONCAT(DISTINCT ?actorName; separator=\",\") AS ?actors) " +
                        "       (GROUP_CONCAT(DISTINCT ?langName; separator=\",\") AS ?languages) " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title . " +
                        "  OPTIONAL { ?film :releaseYear ?year } " +
                        "  OPTIONAL { ?film :imdbRating ?imdb } " +
                        "  OPTIONAL { ?film :directedBy ?dir . ?dir :personName ?director } " +
                        "  OPTIONAL { ?film :hasGenre ?g . ?g :genreName ?genreName } " +
                        "  OPTIONAL { ?film :hasActor ?a . ?a :personName ?actorName } " +
                        "  OPTIONAL { ?film :spokenInLanguage ?l . ?l :languageName ?langName } " +
                        "  FILTER(LCASE(STR(?title)) = LCASE(\"" + filmTitle + "\")) " +
                        "} " +
                        "GROUP BY ?film ?title ?year ?imdb ?director";

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                QuerySolution sol = rs.next();
                return extractFilmMetadata(sol);
            }
        }

        return null;
    }

    private List<FilmMetadata> getAllFilms() {
        List<FilmMetadata> films = new ArrayList<>();

        String sparql =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?title ?year ?imdb ?director " +
                        "       (GROUP_CONCAT(DISTINCT ?genreName; separator=\",\") AS ?genres) " +
                        "       (GROUP_CONCAT(DISTINCT ?actorName; separator=\",\") AS ?actors) " +
                        "       (GROUP_CONCAT(DISTINCT ?langName; separator=\",\") AS ?languages) " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title . " +
                        "  OPTIONAL { ?film :releaseYear ?year } " +
                        "  OPTIONAL { ?film :imdbRating ?imdb } " +
                        "  OPTIONAL { ?film :directedBy ?dir . ?dir :personName ?director } " +
                        "  OPTIONAL { ?film :hasGenre ?g . ?g :genreName ?genreName } " +
                        "  OPTIONAL { ?film :hasActor ?a . ?a :personName ?actorName } " +
                        "  OPTIONAL { ?film :spokenInLanguage ?l . ?l :languageName ?langName } " +
                        "} " +
                        "GROUP BY ?film ?title ?year ?imdb ?director";

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                films.add(extractFilmMetadata(sol));
            }
        }

        return films;
    }

    private FilmMetadata extractFilmMetadata(QuerySolution sol) {
        String title = sol.getLiteral("title").getString();

        int year = 2000;
        if (sol.contains("year")) {
            String yearStr = sol.getLiteral("year").getString();
            year = Integer.parseInt(yearStr.substring(0, 4));
        }

        double imdb = sol.contains("imdb") ? sol.getLiteral("imdb").getDouble() : 0.0;
        String director = sol.contains("director") ? sol.getLiteral("director").getString() : "";

        Set<String> genres = new HashSet<>();
        if (sol.contains("genres")) {
            String genresStr = sol.getLiteral("genres").getString();
            if (!genresStr.isEmpty()) {
                genres.addAll(Arrays.asList(genresStr.split(",")));
            }
        }

        Set<String> actors = new HashSet<>();
        if (sol.contains("actors")) {
            String actorsStr = sol.getLiteral("actors").getString();
            if (!actorsStr.isEmpty()) {
                actors.addAll(Arrays.asList(actorsStr.split(",")));
            }
        }

        Set<String> languages = new HashSet<>();
        if (sol.contains("languages")) {
            String langsStr = sol.getLiteral("languages").getString();
            if (!langsStr.isEmpty()) {
                languages.addAll(Arrays.asList(langsStr.split(",")));
            }
        }

        return new FilmMetadata(title, year, imdb, director, genres, actors, languages);
    }

    private static class FilmMetadata {
        String title;
        int year;
        double imdbRating;
        String director;
        Set<String> genres;
        Set<String> actors;
        Set<String> languages;

        FilmMetadata(String title, int year, double imdb, String director,
                     Set<String> genres, Set<String> actors, Set<String> languages) {
            this.title = title;
            this.year = year;
            this.imdbRating = imdb;
            this.director = director;
            this.genres = genres;
            this.actors = actors;
            this.languages = languages;
        }
    }

    public static class SimilarFilm {
        private String title;
        private double score;
        private FilmMetadata metadata;

        SimilarFilm(String title, double score, FilmMetadata metadata) {
            this.title = title;
            this.score = score;
            this.metadata = metadata;
        }

        public String getTitle() { return title; }
        public double getScore() { return score; }
        public int getYear() { return metadata.year; }
        public double getImdbRating() { return metadata.imdbRating; }
        public String getDirector() { return metadata.director; }
        public Set<String> getGenres() { return metadata.genres; }
    }
}